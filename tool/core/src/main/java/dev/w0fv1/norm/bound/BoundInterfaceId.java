package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundInterfaceId(String value) implements BoundDeclarationId {
  public BoundInterfaceId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundInterfaceId of(SymbolId symbol) {
    return new BoundInterfaceId(symbol.value());
  }
}
