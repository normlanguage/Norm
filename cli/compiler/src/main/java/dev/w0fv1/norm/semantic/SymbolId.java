package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.Objects;

public record SymbolId(String value) {
  public SymbolId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) throw new IllegalArgumentException("symbol id must not be blank");
  }

  public static SymbolId builtin(String key) {
    return new SymbolId("builtin/" + key);
  }

  public static SymbolId source(ModuleSourceCoordinate source, int ordinal) {
    if (ordinal < 0) throw new IllegalArgumentException("symbol ordinal must not be negative");
    return new SymbolId("source/" + source.identity() + "#" + ordinal);
  }

  public static SymbolId authored(String identity) {
    Objects.requireNonNull(identity, "identity");
    if (identity.isBlank())
      throw new IllegalArgumentException("authored identity must not be blank");
    return new SymbolId("authored/" + identity);
  }
}
