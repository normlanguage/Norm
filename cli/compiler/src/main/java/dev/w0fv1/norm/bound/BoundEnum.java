package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundEnum(
    BoundEnumId id,
    String name,
    BoundVisibility visibility,
    SemanticType type,
    List<BoundTypeParameter> typeParameters,
    List<BoundEnumVariant> variants,
    SourceSpan span)
    implements BoundNode {
  public BoundEnum {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(type, "type");
    typeParameters = List.copyOf(typeParameters);
    variants = List.copyOf(variants);
    Objects.requireNonNull(span, "span");
  }
}
