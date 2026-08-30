package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public record BoundEnumField(String name, SemanticType type, int ordinal) {
  public BoundEnumField {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (ordinal < 0) throw new IllegalArgumentException("enum field ordinal must be non-negative");
  }
}
