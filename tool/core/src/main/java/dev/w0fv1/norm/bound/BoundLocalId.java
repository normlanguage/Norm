package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundLocalId(String value) implements BoundDeclarationId {
  public BoundLocalId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundLocalId of(SymbolId symbol) {
    return new BoundLocalId(symbol.value());
  }
}
