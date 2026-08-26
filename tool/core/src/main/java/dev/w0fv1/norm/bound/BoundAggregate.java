package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundAggregate(
    BoundAggregateId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    List<BoundTypeParameter> typeParameters,
    Optional<SemanticType> parentType,
    int fieldCount,
    List<BoundField> fields,
    List<BoundCallableId> methods,
    List<BoundMethodDispatch> dispatch,
    BoundCallableId constructor,
    List<BoundConformance> conformances,
    SourceSpan span)
    implements BoundNode {
  public BoundAggregate {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    typeParameters = List.copyOf(typeParameters);
    parentType = Objects.requireNonNull(parentType, "parentType");
    if (fieldCount < fields.size()) throw new IllegalArgumentException("field count is invalid");
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
    dispatch = List.copyOf(dispatch);
    Objects.requireNonNull(constructor, "constructor");
    conformances = List.copyOf(conformances);
    Objects.requireNonNull(span, "span");
  }
}
