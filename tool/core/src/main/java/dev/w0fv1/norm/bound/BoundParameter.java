package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public record BoundParameter(BoundLocalId id, String name, SemanticType type, int ordinal) {
  public BoundParameter {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (ordinal < 0) throw new IllegalArgumentException("parameter ordinal must be non-negative");
  }
}
