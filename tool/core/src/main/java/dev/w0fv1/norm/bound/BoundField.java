package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public record BoundField(BoundFieldId id, String name, SemanticType type, int ordinal) {
  public BoundField {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (ordinal < 0) throw new IllegalArgumentException("field ordinal must be non-negative");
  }
}
