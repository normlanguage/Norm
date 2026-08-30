package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;

public record BoundRuntimeType(SemanticType type, List<BoundReifiedArgument> reifiedArguments) {
  public BoundRuntimeType {
    Objects.requireNonNull(type, "type");
    reifiedArguments = List.copyOf(reifiedArguments);
  }
}
