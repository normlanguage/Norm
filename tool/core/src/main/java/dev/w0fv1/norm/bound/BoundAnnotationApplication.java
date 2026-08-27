package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundAnnotationApplication(
    BoundAnnotationId annotation,
    BoundAnnotationTarget target,
    List<BoundAnnotationValue> values,
    SourceSpan span) {
  public BoundAnnotationApplication {
    Objects.requireNonNull(annotation, "annotation");
    Objects.requireNonNull(target, "target");
    values = List.copyOf(values);
    Objects.requireNonNull(span, "span");
  }
}
