package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record AnnotationApplication(
    SymbolId annotation, AnnotationSite target, List<AnnotationValue> values, SourceSpan span) {
  public AnnotationApplication {
    Objects.requireNonNull(annotation, "annotation");
    Objects.requireNonNull(target, "target");
    values = List.copyOf(values);
    Objects.requireNonNull(span, "span");
  }
}
