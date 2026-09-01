package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaApiParameter(
    int index, Optional<String> name, int modifiers, List<JavaApiAnnotation> annotations) {
  public JavaApiParameter {
    if (index < 0) throw new IllegalArgumentException("parameter index must not be negative");
    Objects.requireNonNull(name, "name");
    annotations =
        annotations.stream().sorted(Comparator.comparing(JavaApiAnnotation::type)).toList();
  }
}
