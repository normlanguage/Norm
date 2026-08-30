package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundFieldId(String value) implements BoundDeclarationId {
  public BoundFieldId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundFieldId of(SymbolId symbol) {
    return new BoundFieldId(symbol.value());
  }
}
