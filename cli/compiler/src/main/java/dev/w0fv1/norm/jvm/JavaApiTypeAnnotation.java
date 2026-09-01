package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.Optional;

public record JavaApiTypeAnnotation(
    int typeReference, Optional<String> typePath, JavaApiAnnotation annotation) {
  public JavaApiTypeAnnotation {
    Objects.requireNonNull(typePath, "typePath");
    Objects.requireNonNull(annotation, "annotation");
  }
}
