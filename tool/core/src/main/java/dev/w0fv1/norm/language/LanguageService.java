package dev.w0fv1.norm.language;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.stdlib.StandardLibrary;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceLocation;
import java.util.List;
import java.util.Optional;

public final class LanguageService {
  private final Compiler compiler;
  private final CompletionEngine completions = new CompletionEngine();
  private final SignatureHelpResolver signatures = new SignatureHelpResolver();

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
    return complete(document(analysis), offset);
  }

  public List<Completion> complete(DocumentSemanticModel document, int offset) {
    return completions.complete(document, offset);
  }

  public Optional<SignatureHelp> signatureHelp(AnalysisResult analysis, int offset) {
    return signatures.resolve(document(analysis), offset);
  }

  public Optional<SignatureHelp> signatureHelp(DocumentSemanticModel document, int offset) {
    return signatures.resolve(document, offset);
  }

  public Optional<HoverInfo> hover(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> symbol = model.resolvedSymbolAt(offset);
    if (symbol.isPresent()) {
      Symbol value = symbol.orElseThrow();
      String signature = SymbolPresentation.signature(value);
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
    return model.resolvedSymbolAt(offset).flatMap(Symbol::declaration);
  }

  public List<SourceLocation> references(
      AnalysisResult analysis, int offset, boolean includeDeclaration) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> selected = model.symbolAt(offset);
    if (selected.isEmpty()) return List.of();
    Symbol symbol = selected.orElseThrow();
    Optional<SourceLocation> declaration = symbol.declaration();
    List<dev.w0fv1.norm.value.SourceSpan> references =
        model.isAlias(symbol.id())
            ? model.authoringReferences(symbol.id())
            : model.references(symbol.id());
    return references.stream()
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
        model.authoringReferences(selected.orElseThrow().id()).stream()
            .map(span -> span.location())
            .toList();
    return Optional.of(new RenameEdit(newName, locations));
  }

  private static boolean isEditable(Symbol symbol) {
    return symbol.declaration().isPresent()
        && !symbol.declaration().orElseThrow().document().uri().getScheme().equals("stdlib");
  }

  private static DocumentSemanticModel document(AnalysisResult analysis) {
    SemanticModel model = analysis.semanticModel();
    return new DocumentSemanticModel(model.source(), model.syntax(), model.tokens(), model);
  }
}
