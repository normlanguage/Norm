package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record BoundAnnotation(
    BoundAnnotationId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    Set<AnnotationTarget> targets,
    AnnotationRetention retention,
    List<BoundField> fields,
    List<Optional<BoundAnnotationValue>> defaults,
    SourceSpan span) {
  public BoundAnnotation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    targets = Set.copyOf(targets);
    Objects.requireNonNull(retention, "retention");
    fields = List.copyOf(fields);
    defaults = defaults.stream().map(value -> Objects.requireNonNull(value, "default")).toList();
    Objects.requireNonNull(span, "span");
    if (fields.size() != defaults.size()) {
      throw new IllegalArgumentException("annotation fields and defaults must have equal size");
    }
  }
}
