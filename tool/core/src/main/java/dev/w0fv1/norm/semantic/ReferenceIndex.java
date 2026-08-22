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

  public static ReferenceIndex from(
      Map<SourceSpan, SymbolId> bindings, Map<SymbolId, SymbolId> aliasTargets) {
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(aliasTargets, "aliasTargets");
    Map<SymbolId, List<SourceSpan>> grouped = new LinkedHashMap<>();
    bindings.forEach(
        (span, symbol) -> grouped.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(span));
    Map<SymbolId, List<SourceSpan>> immutable = new LinkedHashMap<>();
    grouped.forEach(
        (symbol, spans) -> {
          spans.sort(ORDER);
          immutable.put(symbol, List.copyOf(spans));
        });
    return new ReferenceIndex(Map.copyOf(immutable));
  }

  public List<SourceSpan> references(SymbolId symbol) {
    Objects.requireNonNull(symbol, "symbol");
    return references.getOrDefault(symbol, List.of());
  }
}
