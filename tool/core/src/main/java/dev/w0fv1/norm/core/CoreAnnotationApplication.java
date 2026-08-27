package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreAnnotationApplication(
    DefinitionId annotation, CoreAnnotationTarget target, List<CoreAnnotationValue> values) {
  public CoreAnnotationApplication {
    Objects.requireNonNull(annotation, "annotation");
    Objects.requireNonNull(target, "target");
    values = List.copyOf(values);
  }
}
