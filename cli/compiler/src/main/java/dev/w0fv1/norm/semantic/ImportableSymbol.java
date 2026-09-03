package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record ImportableSymbol(Symbol symbol, String qualifiedName, boolean exported) {
  public ImportableSymbol {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(qualifiedName, "qualifiedName");
    if (qualifiedName.isBlank())
      throw new IllegalArgumentException("qualified name must not be blank");
  }
}
