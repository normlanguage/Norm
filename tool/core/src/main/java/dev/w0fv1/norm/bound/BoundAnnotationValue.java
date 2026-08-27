package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public record BoundAnnotationValue(SemanticType type, Object value) {
  public BoundAnnotationValue {
    Objects.requireNonNull(type, "type");
  }
}
