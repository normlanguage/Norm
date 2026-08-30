package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreArgument(CoreExpression value, int parameterIndex) {
  public CoreArgument {
    Objects.requireNonNull(value, "value");
    if (parameterIndex < 0) {
      throw new IllegalArgumentException("parameter index must not be negative");
    }
  }
}
