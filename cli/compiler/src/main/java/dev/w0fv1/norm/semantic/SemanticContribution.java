package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.TokenSpanMapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SemanticContribution(
    Map<SymbolId, Symbol> symbols,
    Map<SourceSpan, SymbolId> bindings,
    java.util.Set<SourceSpan> declarationOperators,
    Map<SourceSpan, SemanticType> expressionTypes,
    Map<SourceSpan, SemanticType> resultBuilders,
    Map<SourceSpan, ResolvedCall> resolvedCalls,
    Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments,
    Map<SourceSpan, ResolvedIteration> iterations,
    Map<SourceSpan, ResolvedIndex> indexes,
    List<SemanticScope> scopes) {
  public SemanticContribution {
    symbols = Map.copyOf(symbols);
    bindings = Map.copyOf(bindings);
    declarationOperators = java.util.Set.copyOf(declarationOperators);
    expressionTypes = Map.copyOf(expressionTypes);
    resultBuilders = Map.copyOf(resultBuilders);
    resolvedCalls = Map.copyOf(resolvedCalls);
    functionReferenceTypeArguments =
        functionReferenceTypeArguments.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    iterations = Map.copyOf(iterations);
    indexes = Map.copyOf(indexes);
    scopes = List.copyOf(scopes);
  }

  public SemanticContribution rebase(TokenSpanMapping mapping) {
    var previousRoot = mapping.previousRoot();
    var rebasedSymbols = new LinkedHashMap<SymbolId, Symbol>();
    symbols.forEach(
        (id, symbol) -> {
          var location = symbol.declaration();
          if (location.isPresent()
              && location.orElseThrow().document().equals(previousRoot.source().id())
              && location.orElseThrow().startOffset() >= previousRoot.startOffset()
              && location.orElseThrow().endOffset() <= previousRoot.endOffset()) {
            symbol =
                new Symbol(
                    symbol.id(),
                    symbol.name(),
                    symbol.kind(),
                    symbol.type(),
                    java.util.Optional.of(mapping.rebase(location.orElseThrow())),
                    symbol.owner(),
                    symbol.typeParameters(),
                    symbol.parameters(),
                    symbol.documentation(),
                    symbol.accessor());
          }
          rebasedSymbols.put(id, symbol);
        });
    var calls = new LinkedHashMap<SourceSpan, ResolvedCall>();
    resolvedCalls.forEach(
        (span, call) ->
            calls.put(
                mapping.rebase(span),
                new ResolvedCall(
                    call.kind(),
                    call.target(),
                    mapping.rebase(call.calleeSpan()),
                    new ArgumentBinding(
                        call.arguments().parameterIndices(),
                        call.arguments().labels().entrySet().stream()
                            .collect(
                                java.util.stream.Collectors.toMap(
                                    entry -> mapping.rebase(entry.getKey()), Map.Entry::getValue))),
                    call.parameters(),
                    call.callableTypeArguments(),
                    call.resultType())));
    return new SemanticContribution(
        rebasedSymbols,
        rebase(bindings, mapping),
        declarationOperators.stream()
            .map(mapping::rebase)
            .collect(java.util.stream.Collectors.toSet()),
        rebase(expressionTypes, mapping),
        rebase(resultBuilders, mapping),
        calls,
        rebase(functionReferenceTypeArguments, mapping),
        rebase(iterations, mapping),
        rebase(indexes, mapping),
        scopes.stream()
            .map(
                scope ->
                    new SemanticScope(mapping.rebase(scope.span()), scope.depth(), scope.symbols()))
            .toList());
  }

  private static <T> Map<SourceSpan, T> rebase(
      Map<SourceSpan, T> values, TokenSpanMapping mapping) {
    var result = new LinkedHashMap<SourceSpan, T>();
    values.forEach((span, value) -> result.put(mapping.rebase(span), value));
    return result;
  }
}
