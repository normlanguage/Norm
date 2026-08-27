package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record AnnotationValue(SemanticType type, Object value) {
  public AnnotationValue {
    Objects.requireNonNull(type, "type");
  }
}
