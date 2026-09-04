package dev.w0fv1.norm.core;

import java.util.Objects;
import java.util.Optional;

public record CoreTypeParameter(
    int index, Optional<CoreType> upperBound, Optional<CoreType> defaultType) {
  public CoreTypeParameter {
    if (index < 0) throw new IllegalArgumentException("type parameter index must not be negative");
    upperBound = Objects.requireNonNull(upperBound, "upperBound");
    defaultType = Objects.requireNonNull(defaultType, "defaultType");
  }

  public CoreTypeParameter(int index, Optional<CoreType> upperBound) {
    this(index, upperBound, Optional.empty());
  }
}
