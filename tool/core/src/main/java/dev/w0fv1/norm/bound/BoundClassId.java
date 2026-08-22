package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundClassId(String value) implements BoundDeclarationId {
  public BoundClassId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundClassId of(SymbolId symbol) {
    return new BoundClassId(symbol.value());
  }
}
