package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundClass(
    BoundClassId id,
    String name,
    SemanticType type,
    List<BoundField> fields,
    List<BoundCallableId> methods,
    SourceSpan span)
    implements BoundNode {
  public BoundClass {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
    Objects.requireNonNull(span, "span");
  }
}
