package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundAggregate(
    BoundAggregateId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    List<BoundTypeParameter> typeParameters,
    List<BoundField> fields,
    List<BoundCallableId> methods,
    List<BoundConformance> conformances,
    SourceSpan span)
    implements BoundNode {
  public BoundAggregate {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    typeParameters = List.copyOf(typeParameters);
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
    conformances = List.copyOf(conformances);
    Objects.requireNonNull(span, "span");
  }
}
