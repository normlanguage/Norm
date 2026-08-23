package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeRelations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class CompletionEngine {
  private final CompletionContextResolver contexts = new CompletionContextResolver();
  private final ExpectedTypeResolver expectedTypes = new ExpectedTypeResolver();
  private final ImportEditBuilder imports = new ImportEditBuilder();
  private final SymbolSpecializer symbols = new SymbolSpecializer();

  List<Completion> complete(DocumentSemanticModel document, int offset) {
    if (offset < 0 || offset > document.source().length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    SemanticModel model = document.semanticModel();
    CompletionContext context = contexts.resolve(document, offset);
    if (context instanceof CompletionContext.None) return List.of();
    if (context instanceof CompletionContext.Import imported) {
      return withTextEdits(
          importCompletions(model), document, imported.qualifiedNameStart(), offset);
    }
    if (context instanceof CompletionContext.Member member) {
      return withTextEdits(
          memberCompletions(model, document.source().text(), member.dotOffset()),
          document,
          identifierStart(document.source().text(), offset),
          offset);
    }
    List<RankedCompletion> result = new ArrayList<>();
    Optional<SemanticType> expectedType = expectedTypes.resolve(document, offset);
    boolean constructors =
        !(context instanceof CompletionContext.Type
            || context instanceof CompletionContext.TypeArgument
            || context instanceof CompletionContext.TopLevel);
    completionKeywords(context)
        .forEach(completion -> result.add(new RankedCompletion(completion, Optional.empty())));
    List<Symbol> visibleSymbols = model.visibleSymbols(offset);
    Set<String> visibleNames =
        visibleSymbols.stream().map(Symbol::name).collect(java.util.stream.Collectors.toSet());
    visibleSymbols.stream()
        .filter(
            symbol ->
                !(context instanceof CompletionContext.Type
                        || context instanceof CompletionContext.TypeArgument)
                    || symbol.kind() == SymbolKind.TYPE
                    || symbol.kind() == SymbolKind.TYPE_PARAMETER)
        .map(symbol -> ranked(symbol, expectedType, List.of(), constructors))
        .forEach(result::add);
    model.importableSymbols().stream()
        .filter(candidate -> !visibleNames.contains(candidate.symbol().name()))
        .filter(
            candidate ->
                candidate.symbol().declaration().isPresent()
                    && !candidate
                        .symbol()
                        .declaration()
                        .orElseThrow()
                        .document()
                        .equals(document.source().id()))
        .filter(
            candidate ->
                document.syntax().imports().stream()
                    .noneMatch(
                        imported -> imported.qualifiedName().equals(candidate.qualifiedName())))
        .map(
            candidate ->
                rankedImport(
                    candidate.symbol(),
                    expectedType,
                    List.of(imports.create(document, candidate.qualifiedName())),
                    constructors,
                    candidate.qualifiedName()))
        .forEach(result::add);
    if (context instanceof CompletionContext.TopLevel && !visibleNames.contains("main")) {
      result.add(
          new RankedCompletion(
              new Completion(
                  "main",
                  CompletionKind.SNIPPET,
                  "Norm entry point",
                  "",
                  "void main() {\n  ${1}\n}",
                  true),
              Optional.empty()));
    }
    List<Completion> completions =
        result.stream()
            .sorted(
                Comparator.<RankedCompletion>comparingInt(
                        candidate -> relevance(expectedType, candidate))
                    .thenComparing(candidate -> candidate.completion().label()))
            .map(RankedCompletion::completion)
            .toList();
    return withTextEdits(
        completions, document, identifierStart(document.source().text(), offset), offset);
  }

  private static List<Completion> importCompletions(SemanticModel model) {
    return model.importableSymbols().stream()
        .map(
            candidate -> {
              Completion symbol = completion(candidate.symbol());
              return new Completion(
                  candidate.qualifiedName(),
                  symbol.kind(),
                  symbol.detail(),
                  symbol.documentation(),
                  candidate.qualifiedName(),
                  false);
            })
        .sorted(Comparator.comparing(Completion::label))
        .toList();
  }

  private static List<Completion> withTextEdits(
      List<Completion> completions, DocumentSemanticModel document, int start, int end) {
    var location = new dev.w0fv1.norm.value.SourceLocation(document.source().id(), start, end);
    return completions.stream()
        .map(
            completion ->
                completion.withTextEdit(new CompletionTextEdit(location, completion.insertText())))
        .toList();
  }

  private static int identifierStart(String text, int offset) {
    int start = offset;
    while (start > 0 && Character.isUnicodeIdentifierPart(text.charAt(start - 1))) start--;
    return start;
  }

  private static List<Completion> completionKeywords(CompletionContext context) {
    if (context instanceof CompletionContext.TopLevel) {
      return List.of(
          snippet("class", "Norm class", "class ${1:Name} {\n  ${2}\n}"),
          snippet("enum", "Norm enum", "enum ${1:Name} {\n  ${2:Value}\n}"),
          keyword("package"),
          keyword("import"),
          keyword("public"),
          keyword("private"));
    }
    if (context instanceof CompletionContext.Statement) {
      return List.of(
          snippet("if", "Norm conditional", "if ${1:condition} {\n  ${2}\n}"),
          snippet("for", "Norm loop", "for ${1:item}: ${2:values} {\n  ${3}\n}"),
          keyword("return"),
          keyword("break"),
          keyword("continue"),
          keyword("true"),
          keyword("false"));
    }
    if (context instanceof CompletionContext.Expression
        || context instanceof CompletionContext.ArgumentLabel) {
      return List.of(keyword("true"), keyword("false"));
    }
    return List.of();
  }

  private static Completion keyword(String label) {
    return new Completion(label, CompletionKind.KEYWORD, "Norm keyword", "", label, false);
  }

  private static Completion snippet(String label, String detail, String insertText) {
    return new Completion(label, CompletionKind.SNIPPET, detail, "", insertText, true);
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
        .map(CompletionEngine::completion)
        .sorted(Comparator.comparing(Completion::label))
        .toList();
  }

  private static Completion completion(Symbol symbol) {
    return completion(symbol, List.of(), false);
  }

  private static Completion completion(
      Symbol symbol, List<CompletionTextEdit> additionalTextEdits, boolean constructor) {
    CompletionKind kind =
        switch (symbol.kind()) {
          case TYPE, TYPE_PARAMETER -> CompletionKind.TYPE;
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
    } else if (symbol.kind() == SymbolKind.TYPE
        && constructor
        && (!symbol.typeParameters().isEmpty() || !symbol.parameters().isEmpty())) {
      int firstParameter = 1;
      String typeArguments = "";
      if (!symbol.typeParameters().isEmpty()) {
        if (!symbol.type().arguments().isEmpty()) {
          typeArguments =
              "<"
                  + symbol.type().arguments().stream()
                      .map(SemanticType::displayName)
                      .collect(java.util.stream.Collectors.joining(", "))
                  + ">";
        } else {
          typeArguments =
              "<"
                  + java.util.stream.IntStream.range(0, symbol.typeParameters().size())
                      .mapToObj(
                          index ->
                              "${" + (index + 1) + ":" + symbol.typeParameters().get(index) + "}")
                      .collect(java.util.stream.Collectors.joining(", "))
                  + ">";
          firstParameter += symbol.typeParameters().size();
        }
      }
      int parameterStart = firstParameter;
      String arguments =
          java.util.stream.IntStream.range(0, symbol.parameters().size())
              .mapToObj(
                  index -> {
                    String name = symbol.parameters().get(index).name();
                    return name + ": ${" + (parameterStart + index) + ":" + name + "}";
                  })
              .collect(java.util.stream.Collectors.joining(", "));
      insertText += typeArguments + "(" + arguments + ")";
      snippet = true;
    }
    return new Completion(
        symbol.name(),
        kind,
        SymbolPresentation.signature(symbol),
        symbol.documentation(),
        insertText,
        snippet,
        additionalTextEdits);
  }

  private RankedCompletion ranked(
      Symbol symbol,
      Optional<SemanticType> expected,
      List<CompletionTextEdit> additionalTextEdits,
      boolean constructor) {
    Symbol candidate = symbol;
    if (symbol.kind() == SymbolKind.TYPE
        && expected.isPresent()
        && expected.orElseThrow().identity().equals(symbol.type().identity())) {
      candidate = symbols.specialize(symbol, expected.orElseThrow().arguments());
    }
    return new RankedCompletion(
        completion(candidate, additionalTextEdits, constructor), Optional.of(candidate.type()));
  }

  private RankedCompletion rankedImport(
      Symbol symbol,
      Optional<SemanticType> expected,
      List<CompletionTextEdit> additionalTextEdits,
      boolean constructor,
      String qualifiedName) {
    RankedCompletion ranked = ranked(symbol, expected, additionalTextEdits, constructor);
    Completion completion = ranked.completion();
    return new RankedCompletion(
        new Completion(
            completion.label(),
            completion.kind(),
            completion.detail() + " — " + qualifiedName,
            completion.documentation(),
            completion.insertText(),
            completion.snippet(),
            completion.additionalTextEdits()),
        ranked.type());
  }

  private static int relevance(Optional<SemanticType> expected, RankedCompletion candidate) {
    int typeRank = 1;
    if (expected.isPresent() && candidate.type().isPresent()) {
      typeRank =
          TypeRelations.isAssignable(expected.orElseThrow(), candidate.type().orElseThrow())
              ? 0
              : 2;
    }
    int kindRank =
        switch (candidate.completion().kind()) {
          case VARIABLE -> 0;
          case FIELD, PROPERTY, ENUM_MEMBER -> 1;
          case METHOD, FUNCTION -> 2;
          case TYPE -> 3;
          case KEYWORD -> 4;
          case SNIPPET -> 5;
        };
    return typeRank * 10 + kindRank;
  }

  private record RankedCompletion(Completion completion, Optional<SemanticType> type) {}
}
