package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundInterfaceMethodId(String value) implements BoundDeclarationId {
  public BoundInterfaceMethodId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundInterfaceMethodId of(SymbolId symbol) {
    return new BoundInterfaceMethodId(symbol.value());
  }
}
