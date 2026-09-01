package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationEnumValue(String type, String constant) implements JavaAnnotationValue {
  public JavaAnnotationEnumValue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(constant, "constant");
  }
}
