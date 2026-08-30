package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundBuiltinConformance(
    List<BoundTypeParameter> typeParameters,
    SemanticType concreteType,
    SemanticType interfaceType,
    List<BoundWitness> witnesses,
    SourceSpan span) {
  public BoundBuiltinConformance {
    typeParameters = List.copyOf(typeParameters);
    Objects.requireNonNull(concreteType, "concreteType");
    Objects.requireNonNull(interfaceType, "interfaceType");
    witnesses = List.copyOf(witnesses);
    Objects.requireNonNull(span, "span");
  }
}
