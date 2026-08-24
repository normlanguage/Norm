package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundEnumVariantId(String value) implements BoundDeclarationId {
  public BoundEnumVariantId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundEnumVariantId of(SymbolId symbol) {
    return new BoundEnumVariantId(symbol.value());
  }
}
