package dev.w0fv1.norm.bound;

import java.util.List;
import java.util.Objects;

public record BoundInterceptor(BoundAggregateId annotation, List<BoundAnnotationValue> values) {
  public BoundInterceptor {
    Objects.requireNonNull(annotation, "annotation");
    values = List.copyOf(values);
  }
}
