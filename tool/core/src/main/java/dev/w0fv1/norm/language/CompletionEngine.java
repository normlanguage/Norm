package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeRelations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
          importCompletions(model, document), document, imported.qualifiedNameStart(), offset);
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
        .forEach(completion -> result.add(new RankedCompletion(completion, Optional.empty(), 0)));
    List<Symbol> visibleSymbols = model.visibleSymbols(offset);
    Set<String> visibleNames =
        visibleSymbols.stream().map(Symbol::name).collect(java.util.stream.Collectors.toSet());
    visibleSymbols.stream()
        .flatMap(symbol -> model.callableAlternatives(symbol).stream())
        .filter(symbol -> isCandidateForContext(context, symbol))
        .map(symbol -> ranked(symbol, expectedType, List.of(), constructors))
        .forEach(result::add);
    model.importableSymbols(document.source().id()).stream()
        .filter(candidate -> isCandidateForContext(context, candidate.symbol()))
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
                  "main() {\n  ${1}\n}",
                  true),
              Optional.empty(),
              0));
    }
    List<RankedCompletion> ranked =
        result.stream()
            .sorted(
                Comparator.<RankedCompletion>comparingInt(
                        candidate -> relevance(expectedType, candidate))
                    .thenComparing(candidate -> candidate.completion().label())
                    .thenComparingInt(RankedCompletion::arity))
            .toList();
    Map<String, Completion> unique = new LinkedHashMap<>();
    ranked.forEach(
        candidate ->
            unique.putIfAbsent(
                candidate.completion().kind()
                    + "\u0000"
                    + candidate.completion().label()
                    + "\u0000"
                    + candidate.completion().additionalTextEdits(),
                candidate.completion()));
    return withTextEdits(
        List.copyOf(unique.values()),
        document,
        identifierStart(document.source().text(), offset),
        offset);
  }

  private static List<Completion> importCompletions(
      SemanticModel model, DocumentSemanticModel document) {
    return model.importableSymbols(document.source().id()).stream()
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
          snippet("value", "Norm value", "value ${1:Name} {\n  ${2}\n}"),
          snippet("enum", "Norm enum", "enum ${1:Name} {\n  ${2:Value}\n}"),
          snippet("interface", "Norm interface", "interface ${1:Name} {\n  ${2}\n}"),
          keyword("package"),
          keyword("import"),
          keyword("public"),
          keyword("private"));
    }
    if (context instanceof CompletionContext.Statement) {
      return List.of(
          snippet("if", "Norm conditional", "if ${1:condition} {\n  ${2}\n}"),
          snippet(
              "switch",
              "Norm switch expression",
              "switch ${1:value} {\n  case ${2:_} {\n    ${3}\n  }\n}"),
          keyword("case"),
          snippet("for", "Norm loop", "for ${1:item}: ${2:values} {\n  ${3}\n}"),
          keyword("return"),
          keyword("break"),
          keyword("continue"),
          keyword("ref"),
          keyword("true"),
          keyword("false"),
          keyword("null"));
    }
    if (context instanceof CompletionContext.Expression
        || context instanceof CompletionContext.ArgumentLabel) {
      return List.of(
          snippet(
              "switch",
              "Norm switch expression",
              "switch ${1:value} {\n  case ${2:_} {\n    break ${3:value}\n  }\n}"),
          keyword("true"),
          keyword("false"),
          keyword("null"));
    }
    return List.of();
  }

  private static Completion keyword(String label) {
    return new Completion(label, CompletionKind.KEYWORD, "Norm keyword", "", label, false);
  }

  private static boolean isCandidateForContext(CompletionContext context, Symbol symbol) {
    if (context instanceof CompletionContext.InterfaceType) {
      return symbol.kind() == SymbolKind.INTERFACE;
    }
    if (context instanceof CompletionContext.Type
        || context instanceof CompletionContext.TypeArgument) {
      return symbol.kind() == SymbolKind.TYPE
          || symbol.kind() == SymbolKind.INTERFACE
          || symbol.kind() == SymbolKind.TYPE_PARAMETER;
    }
    return true;
  }

  private static Completion snippet(String label, String detail, String insertText) {
    return new Completion(label, CompletionKind.SNIPPET, detail, "", insertText, true);
  }

  private static List<Completion> memberCompletions(
      SemanticModel model, String text, int dotOffset) {
    boolean methodReference = text.charAt(dotOffset) == ':';
    int receiverEnd =
        !methodReference && dotOffset > 0 && text.charAt(dotOffset - 1) == '?'
            ? dotOffset - 1
            : dotOffset;
    int start = receiverEnd;
    while (start > 0 && Character.isUnicodeIdentifierPart(text.charAt(start - 1))) start--;
    int identifierStart = start;
    int receiverOffset = Math.max(0, receiverEnd - 1);
    String receiverName = text.substring(identifierStart, receiverEnd);
    Optional<Symbol> receiverSymbol =
        identifierStart == receiverEnd
            ? Optional.empty()
            : model
                .symbolAt(identifierStart)
                .or(
                    () ->
                        model.visibleSymbols(dotOffset).stream()
                            .filter(symbol -> symbol.name().equals(receiverName))
                            .findFirst());
    boolean typeReceiver =
        receiverSymbol.isPresent() && receiverSymbol.orElseThrow().kind() == SymbolKind.TYPE;
    List<Symbol> typeMembers = model.typeMembers(receiverName);
    if (typeReceiver && !typeMembers.isEmpty()) {
      return symbolCompletions(typeMembers.stream(), methodReference);
    }
    Optional<SemanticType> receiverType =
        identifierStart > 0 && text.charAt(identifierStart - 1) == '.'
            ? model.typeAt(receiverOffset).or(() -> receiverSymbol.map(Symbol::type))
            : receiverSymbol
                .map(Symbol::type)
                .or(() -> model.typeAt(receiverOffset))
                .or(
                    () -> {
                      if (identifierStart == receiverEnd) return Optional.empty();
                      return model
                          .typeAt(identifierStart)
                          .or(() -> model.symbolAt(identifierStart).map(Symbol::type));
                    });
    return symbolCompletions(
        receiverType.stream().flatMap(type -> model.members(type).stream()), methodReference);
  }

  private static List<Completion> symbolCompletions(Stream<Symbol> symbols) {
    return symbolCompletions(symbols, false);
  }

  private static List<Completion> symbolCompletions(
      Stream<Symbol> symbols, boolean methodReference) {
    Map<String, Completion> unique = new LinkedHashMap<>();
    symbols
        .filter(symbol -> !methodReference || callable(symbol))
        .sorted(
            Comparator.comparing(Symbol::name)
                .thenComparingInt(symbol -> symbol.parameters().size()))
        .forEach(
            symbol ->
                unique.putIfAbsent(
                    symbol.kind() + "\u0000" + symbol.name(),
                    methodReference ? referenceCompletion(symbol) : completion(symbol)));
    return List.copyOf(unique.values());
  }

  private static boolean callable(Symbol symbol) {
    return symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.INTERFACE_METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD;
  }

  private static Completion referenceCompletion(Symbol symbol) {
    Completion completion = completion(symbol);
    return new Completion(
        completion.label(),
        completion.kind(),
        completion.detail(),
        completion.documentation(),
        symbol.name(),
        false);
  }

  private static Completion completion(Symbol symbol) {
    return completion(symbol, List.of(), false);
  }

  private static Completion completion(
      Symbol symbol, List<CompletionTextEdit> additionalTextEdits, boolean constructor) {
    if (symbol.type().isFunction()) {
      Symbol callable = SymbolPresentation.callable(symbol);
      String arguments =
          java.util.stream.IntStream.range(0, callable.parameters().size())
              .mapToObj(
                  index -> "${" + (index + 1) + ":" + callable.parameters().get(index).name() + "}")
              .collect(java.util.stream.Collectors.joining(", "));
      return new Completion(
          symbol.name(),
          CompletionKind.FUNCTION,
          SymbolPresentation.signature(callable),
          symbol.documentation(),
          symbol.name() + "(" + arguments + ")",
          true,
          additionalTextEdits);
    }
    CompletionKind kind =
        switch (symbol.kind()) {
          case TYPE, TYPE_PARAMETER -> CompletionKind.TYPE;
          case INTERFACE -> CompletionKind.INTERFACE;
          case FUNCTION, CONSTRUCTOR -> CompletionKind.FUNCTION;
          case METHOD, INTERFACE_METHOD, TYPE_METHOD -> CompletionKind.METHOD;
          case FIELD -> CompletionKind.FIELD;
          case PROPERTY -> CompletionKind.PROPERTY;
          case ENUM_VARIANT -> CompletionKind.ENUM_VARIANT;
          case PARAMETER, LOCAL_VARIABLE, SELF -> CompletionKind.VARIABLE;
        };
    boolean snippet = !symbol.parameters().isEmpty();
    String insertText = symbol.name();
    if (symbol.kind() == SymbolKind.ENUM_VARIANT && !symbol.parameters().isEmpty()) {
      String arguments =
          java.util.stream.IntStream.range(0, symbol.parameters().size())
              .mapToObj(
                  index -> {
                    String name = symbol.parameters().get(index).name();
                    return name + ": ${" + (index + 1) + ":" + name + "}";
                  })
              .collect(java.util.stream.Collectors.joining(", "));
      insertText += "(" + arguments + ")";
      snippet = true;
    } else if (symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.INTERFACE_METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD
        || symbol.kind() == SymbolKind.FUNCTION) {
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
                              "${"
                                  + (index + 1)
                                  + ":"
                                  + symbol.typeParameters().get(index).name()
                                  + "}")
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
        completion(candidate, additionalTextEdits, constructor),
        Optional.of(candidate.type()),
        candidate.parameters().size());
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
        ranked.type(),
        ranked.arity());
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
          case FIELD, PROPERTY, ENUM_VARIANT -> 1;
          case METHOD, FUNCTION -> 2;
          case TYPE, INTERFACE -> 3;
          case KEYWORD -> 4;
          case SNIPPET -> 5;
        };
    return typeRank * 10 + kindRank;
  }

  private record RankedCompletion(Completion completion, Optional<SemanticType> type, int arity) {}
}
