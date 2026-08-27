package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundAnnotationId(String value) {
  public BoundAnnotationId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundAnnotationId of(SymbolId symbol) {
    return new BoundAnnotationId(symbol.value());
  }
}
