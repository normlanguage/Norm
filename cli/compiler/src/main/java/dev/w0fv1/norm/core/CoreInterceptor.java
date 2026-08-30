package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreInterceptor(CoreDefinitionLink annotation, List<CoreAnnotationValue> values) {
  public CoreInterceptor {
    Objects.requireNonNull(annotation, "annotation");
    values = List.copyOf(values);
  }
}
