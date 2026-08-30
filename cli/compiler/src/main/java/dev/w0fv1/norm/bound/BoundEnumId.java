package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundEnumId(String value) implements BoundDeclarationId {
  public BoundEnumId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundEnumId of(SymbolId symbol) {
    return new BoundEnumId(symbol.value());
  }
}
