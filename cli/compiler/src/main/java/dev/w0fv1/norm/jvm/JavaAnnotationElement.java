package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationElement(String name, JavaAnnotationValue value) {
  public JavaAnnotationElement {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
  }
}
