package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record SemanticScope(SourceSpan span, int depth, List<SymbolId> symbols) {
  public SemanticScope {
    Objects.requireNonNull(span, "span");
    if (depth < 0) throw new IllegalArgumentException("scope depth must not be negative");
    symbols = List.copyOf(symbols);
  }
}
