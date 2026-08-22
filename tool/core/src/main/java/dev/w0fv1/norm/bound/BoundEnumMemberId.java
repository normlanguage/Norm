package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.Objects;

public record BoundEnumMemberId(String value) implements BoundDeclarationId {
  public BoundEnumMemberId {
    Objects.requireNonNull(value, "value");
  }

  public static BoundEnumMemberId of(SymbolId symbol) {
    return new BoundEnumMemberId(symbol.value());
  }
}
