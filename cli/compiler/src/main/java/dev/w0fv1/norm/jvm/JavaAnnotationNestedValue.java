package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationNestedValue(JavaApiAnnotation annotation)
    implements JavaAnnotationValue {
  public JavaAnnotationNestedValue {
    Objects.requireNonNull(annotation, "annotation");
  }
}
