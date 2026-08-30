package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundInterfaceMethod(
    BoundInterfaceMethodId id,
    String name,
    List<BoundTypeParameter> typeParameters,
    List<BoundParameter> parameters,
    SemanticType returnType,
    SourceSpan span) {
  public BoundInterfaceMethod {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    Objects.requireNonNull(returnType, "returnType");
    Objects.requireNonNull(span, "span");
  }
}
