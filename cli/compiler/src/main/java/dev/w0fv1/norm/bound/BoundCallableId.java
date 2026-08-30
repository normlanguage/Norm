package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundCallableId(String value) implements BoundDeclarationId {
  public BoundCallableId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundCallableId of(SymbolId symbol) {
    return new BoundCallableId(symbol.value());
  }
}
