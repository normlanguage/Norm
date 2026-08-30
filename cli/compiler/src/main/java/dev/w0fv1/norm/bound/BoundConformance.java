package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;

public record BoundConformance(SemanticType interfaceType, List<BoundWitness> witnesses) {
  public BoundConformance {
    Objects.requireNonNull(interfaceType, "interfaceType");
    witnesses = List.copyOf(witnesses);
  }
}
