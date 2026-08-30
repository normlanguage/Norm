package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundInterface(
    BoundInterfaceId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    List<BoundTypeParameter> typeParameters,
    List<SemanticType> parents,
    List<BoundInterfaceMethod> methods,
    SourceSpan span)
    implements BoundNode {
  public BoundInterface {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    typeParameters = List.copyOf(typeParameters);
    parents = List.copyOf(parents);
    methods = List.copyOf(methods);
    Objects.requireNonNull(span, "span");
  }
}
