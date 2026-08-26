package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundAggregateId(String value) implements BoundDeclarationId {
  public BoundAggregateId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundAggregateId of(SymbolId symbol) {
    return new BoundAggregateId(symbol.value());
  }
}
