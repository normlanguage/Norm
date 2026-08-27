package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreAnnotationValue(CoreType type, Object value) {
  public CoreAnnotationValue {
    Objects.requireNonNull(type, "type");
  }
}
