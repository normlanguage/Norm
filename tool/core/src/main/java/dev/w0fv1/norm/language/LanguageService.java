package dev.w0fv1.norm.language;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.stdlib.StandardLibrary;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LanguageService {
  private final Compiler compiler;
  private final CompletionContextResolver completionContexts = new CompletionContextResolver();

  public LanguageService() {
    this(new Compiler());
  }

  public LanguageService(Compiler compiler) {
    this.compiler = java.util.Objects.requireNonNull(compiler, "compiler");
  }

  public AnalysisResult analyze(SourceFile source) {
    return compiler.analyze(source);
  }

  public AnalysisResult analyze(CompilationRequest request) {
    return compiler.analyze(request);
  }

  public CompilationSnapshot snapshot(CompilationRequest request) {
    return compiler.snapshot(request);
  }

  public Optional<String> standardLibrarySource(DocumentId document) {
    return StandardLibrary.source(document).map(SourceFile::text);
  }

  public List<Completion> complete(AnalysisResult analysis, int offset) {
    SourceFile source = analysis.semanticModel().source();
    if (offset < 0 || offset > source.length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    DocumentSemanticModel document =
        new DocumentSemanticModel(
            source,
            analysis.semanticModel().syntax(),
            analysis.semanticModel().tokens(),
            analysis.semanticModel());
    return complete(document, analysis, offset);
  }

  public List<Completion> complete(
      DocumentSemanticModel document, AnalysisResult analysis, int offset) {
    SourceFile source = document.source();
    if (offset < 0 || offset > source.length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    CompletionContext context = completionContexts.resolve(document, offset);
    if (context instanceof CompletionContext.None || context instanceof CompletionContext.Import)
      return List.of();
    if (context instanceof CompletionContext.Member member)
      return memberCompletions(analysis.semanticModel(), source.text(), member.dotOffset());
    List<Completion> result = new ArrayList<>();
    completionKeywords(context)
        .forEach(
            keyword ->
                result.add(
                    new Completion(
                        keyword, CompletionKind.KEYWORD, "Norm keyword", "", keyword, false)));
    analysis.semanticModel().visibleSymbols(offset).stream()
        .filter(
            symbol ->
                !(context instanceof CompletionContext.Type
                        || context instanceof CompletionContext.TypeArgument)
                    || symbol.kind() == SymbolKind.TYPE
                    || symbol.kind() == SymbolKind.TYPE_PARAMETER)
        .map(LanguageService::completion)
        .forEach(result::add);
    if (context instanceof CompletionContext.TopLevel) {
      result.add(
          new Completion(
              "main",
              CompletionKind.SNIPPET,
              "Norm entry point",
              "",
              "void main() {\n  ${1}\n}",
              true));
    }
    return result.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Completion::label, value -> value, (first, ignored) -> first))
        .values()
        .stream()
        .sorted(Comparator.comparing(Completion::label))
        .toList();
  }

  private static List<String> completionKeywords(CompletionContext context) {
    if (context instanceof CompletionContext.TopLevel) {
      return List.of("class", "enum", "package", "import", "public", "private");
    }
    if (context instanceof CompletionContext.Statement) {
      return List.of("if", "for", "return", "break", "continue", "true", "false");
    }
    if (context instanceof CompletionContext.Expression
        || context instanceof CompletionContext.ArgumentLabel) {
      return List.of("true", "false");
    }
    return List.of();
  }

  public Optional<HoverInfo> hover(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> symbol = model.symbolAt(offset);
    if (symbol.isPresent()) {
      Symbol value = model.resolveAlias(symbol.orElseThrow());
      String signature = signature(value);
      String markdown =
          value.documentation().isBlank()
              ? "`" + signature + "`"
              : "`" + signature + "`\n\n" + value.documentation();
      return Optional.of(new HoverInfo(markdown, value.declaration()));
    }
    return model
        .typeAt(offset)
        .map(SemanticType::displayName)
        .map(type -> new HoverInfo("`" + type + "`", Optional.empty()));
  }

  public Optional<SourceLocation> definition(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    return model.symbolAt(offset).map(model::resolveAlias).flatMap(Symbol::declaration);
  }

  public List<SourceLocation> references(
      AnalysisResult analysis, int offset, boolean includeDeclaration) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> selected = model.symbolAt(offset);
    if (selected.isEmpty()) return List.of();
    Symbol symbol = selected.orElseThrow();
    Optional<SourceLocation> declaration = symbol.declaration();
    return model.references(symbol.id()).stream()
        .map(span -> span.location())
        .filter(location -> includeDeclaration || !declaration.equals(Optional.of(location)))
        .toList();
  }

  public Optional<RenameTarget> prepareRename(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> symbol = model.symbolAt(offset);
    if (symbol.isEmpty() || !isEditable(symbol.orElseThrow())) return Optional.empty();
    return model
        .referenceAt(offset)
        .map(reference -> new RenameTarget(reference.location(), symbol.orElseThrow().name()));
  }

  public Optional<RenameEdit> rename(AnalysisResult analysis, int offset, String newName) {
    if (!LanguageSyntax.isIdentifier(newName)) {
      throw new IllegalArgumentException("rename target must be a valid Norm identifier");
    }
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> selected = model.symbolAt(offset);
    if (selected.isEmpty() || !isEditable(selected.orElseThrow())) return Optional.empty();
    if (model.hasRenameConflict(selected.orElseThrow().id(), newName)) {
      throw new IllegalArgumentException(
          "name '" + newName + "' is already declared in this scope");
    }
    List<SourceLocation> locations =
        model.references(selected.orElseThrow().id()).stream()
            .map(span -> span.location())
            .toList();
    return Optional.of(new RenameEdit(newName, locations));
  }

  private static List<Completion> memberCompletions(
      SemanticModel model, String text, int dotOffset) {
    int start = dotOffset;
    while (start > 0 && Character.isUnicodeIdentifierPart(text.charAt(start - 1))) start--;
    int identifierStart = start;
    int receiverOffset = Math.max(0, dotOffset - 1);
    Optional<SemanticType> receiverType =
        model
            .typeAt(receiverOffset)
            .or(
                () -> {
                  if (identifierStart == dotOffset) return Optional.empty();
                  return model
                      .typeAt(identifierStart)
                      .or(() -> model.symbolAt(identifierStart).map(Symbol::type));
                });
    return receiverType.stream()
        .flatMap(type -> model.members(type).stream())
        .map(LanguageService::completion)
        .sorted(Comparator.comparing(Completion::label))
        .toList();
  }

  private static boolean isEditable(Symbol symbol) {
    return symbol.declaration().isPresent()
        && !symbol.declaration().orElseThrow().document().uri().getScheme().equals("stdlib");
  }

  private static Completion completion(Symbol symbol) {
    CompletionKind kind =
        switch (symbol.kind()) {
          case TYPE -> CompletionKind.TYPE;
          case TYPE_PARAMETER -> CompletionKind.TYPE;
          case FUNCTION -> CompletionKind.FUNCTION;
          case METHOD -> CompletionKind.METHOD;
          case FIELD -> CompletionKind.FIELD;
          case PROPERTY -> CompletionKind.PROPERTY;
          case ENUM_MEMBER -> CompletionKind.ENUM_MEMBER;
          case PARAMETER, LOCAL_VARIABLE -> CompletionKind.VARIABLE;
        };
    boolean snippet = !symbol.parameters().isEmpty();
    String insertText = symbol.name();
    if (symbol.kind() == SymbolKind.METHOD || symbol.kind() == SymbolKind.FUNCTION) {
      String arguments =
          java.util.stream.IntStream.range(0, symbol.parameters().size())
              .mapToObj(
                  index -> {
                    String name = symbol.parameters().get(index).name();
                    String placeholder = "${" + (index + 1) + ":" + name + "}";
                    return symbol.parameters().size() > 1 ? name + ": " + placeholder : placeholder;
                  })
              .collect(java.util.stream.Collectors.joining(", "));
      insertText += "(" + arguments + ")";
      snippet = true;
    }
    return new Completion(
        symbol.name(), kind, signature(symbol), symbol.documentation(), insertText, snippet);
  }

  private static String signature(Symbol symbol) {
    String typeParameters =
        symbol.typeParameters().isEmpty()
            ? ""
            : "<" + String.join(", ", symbol.typeParameters()) + ">";
    if (symbol.kind() == SymbolKind.FUNCTION || symbol.kind() == SymbolKind.METHOD) {
      String parameters =
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName() + " " + parameter.name())
              .collect(java.util.stream.Collectors.joining(", "));
      return symbol.type().displayName()
          + " "
          + symbol.name()
          + typeParameters
          + "("
          + parameters
          + ")";
    }
    if (symbol.kind() == SymbolKind.TYPE) return symbol.name() + typeParameters;
    return symbol.type().displayName() + " " + symbol.name();
  }
}
