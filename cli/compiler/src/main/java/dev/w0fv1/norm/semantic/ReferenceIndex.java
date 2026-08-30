package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReferenceIndex {
  private static final Comparator<SourceSpan> ORDER =
      Comparator.comparing((SourceSpan span) -> span.source().id().uri().toString())
          .thenComparingInt(SourceSpan::startOffset)
          .thenComparingInt(SourceSpan::endOffset);
  private final Map<SymbolId, List<SourceSpan>> references;

  private ReferenceIndex(Map<SymbolId, List<SourceSpan>> references) {
    this.references = references;
  }

  public static ReferenceIndex from(Map<SourceSpan, SymbolId> bindings) {
    Objects.requireNonNull(bindings, "bindings");
    Map<SymbolId, List<SourceSpan>> grouped = new LinkedHashMap<>();
    bindings.forEach(
        (span, symbol) -> grouped.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(span));
    return create(grouped);
  }

  public static ReferenceIndex semantic(
      Map<SourceSpan, SymbolId> bindings,
      Map<SymbolId, List<SymbolId>> aliasTargets,
      Map<SourceSpan, ResolvedCall> calls) {
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(aliasTargets, "aliasTargets");
    Objects.requireNonNull(calls, "calls");
    Map<SourceSpan, SymbolId> callTargets = new LinkedHashMap<>();
    calls.values().forEach(call -> callTargets.put(call.calleeSpan(), call.target()));
    Map<SymbolId, List<SourceSpan>> grouped = new LinkedHashMap<>();
    bindings.forEach(
        (span, symbol) -> {
          SymbolId callTarget = callTargets.get(span);
          List<SymbolId> targets =
              callTarget != null
                  ? List.of(callTarget)
                  : aliasTargets.getOrDefault(symbol, List.of(symbol));
          targets.forEach(
              target -> grouped.computeIfAbsent(target, ignored -> new ArrayList<>()).add(span));
        });
    return create(grouped);
  }

  private static ReferenceIndex create(Map<SymbolId, List<SourceSpan>> grouped) {
    Map<SymbolId, List<SourceSpan>> immutable = new LinkedHashMap<>();
    grouped.forEach(
        (symbol, spans) -> {
          spans.sort(ORDER);
          immutable.put(symbol, spans.stream().distinct().toList());
        });
    return new ReferenceIndex(Map.copyOf(immutable));
  }

  public List<SourceSpan> references(SymbolId symbol) {
    Objects.requireNonNull(symbol, "symbol");
    return references.getOrDefault(symbol, List.of());
  }
}
