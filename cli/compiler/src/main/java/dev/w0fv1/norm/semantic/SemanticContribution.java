package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Map;

public record SemanticContribution(
    Map<SymbolId, Symbol> symbols,
    Map<SourceSpan, SymbolId> bindings,
    Map<SourceSpan, SemanticType> expressionTypes,
    Map<SourceSpan, ResolvedCall> resolvedCalls,
    Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments,
    Map<SourceSpan, ResolvedIteration> iterations,
    Map<SourceSpan, ResolvedIndex> indexes,
    List<SemanticScope> scopes) {
  public SemanticContribution {
    symbols = Map.copyOf(symbols);
    bindings = Map.copyOf(bindings);
    expressionTypes = Map.copyOf(expressionTypes);
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
}
