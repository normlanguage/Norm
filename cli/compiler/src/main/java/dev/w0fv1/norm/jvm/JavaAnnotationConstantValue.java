package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationConstantValue(Object value) implements JavaAnnotationValue {
  public JavaAnnotationConstantValue {
    Objects.requireNonNull(value, "value");
    if (!(value instanceof Boolean
        || value instanceof Byte
        || value instanceof Character
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof Float
        || value instanceof Double
        || value instanceof String)) {
      throw new IllegalArgumentException("unsupported annotation constant " + value.getClass());
    }
  }
}
