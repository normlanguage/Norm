package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;
import java.util.Optional;

public record BoundEnumField(
    ParameterInfo declaration, int ordinal, Optional<BoundCallableId> defaultValue) {
  public BoundEnumField {
    Objects.requireNonNull(declaration, "declaration");
    if (ordinal < 0) throw new IllegalArgumentException("enum field ordinal must be non-negative");
    defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    if (declaration.policy().hasDefault() != defaultValue.isPresent()) {
      throw new IllegalArgumentException("default implementation must match the enum field policy");
    }
  }

  public String name() {
    return declaration.name();
  }

  public SemanticType type() {
    return declaration.type();
  }
}
