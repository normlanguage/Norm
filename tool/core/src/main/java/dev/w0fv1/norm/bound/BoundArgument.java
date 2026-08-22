package dev.w0fv1.norm.bound;

import java.util.Objects;

public record BoundArgument(
    BoundExpression value, int parameterIndex, BoundValueTransfer transfer) {
  public BoundArgument {
    Objects.requireNonNull(value, "value");
    if (parameterIndex < 0)
      throw new IllegalArgumentException("parameter index must be non-negative");
    Objects.requireNonNull(transfer, "transfer");
  }
}
