package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundClass(
    BoundClassId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    List<SemanticType> typeParameters,
    List<BoundField> fields,
    List<BoundCallableId> methods,
    SourceSpan span)
    implements BoundNode {
  public BoundClass {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    typeParameters = List.copyOf(typeParameters);
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
    Objects.requireNonNull(span, "span");
  }
}
