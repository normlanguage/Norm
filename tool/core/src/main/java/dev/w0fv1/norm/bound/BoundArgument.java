package dev.w0fv1.norm.bound;

import java.util.Objects;

public record BoundArgument(BoundExpression value, int parameterIndex) {
  public BoundArgument {
    Objects.requireNonNull(value, "value");
    if (parameterIndex < 0)
      throw new IllegalArgumentException("parameter index must be non-negative");
  }
}
