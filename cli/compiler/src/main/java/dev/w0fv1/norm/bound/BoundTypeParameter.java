package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;
import java.util.Optional;

public record BoundTypeParameter(
    SemanticType type, Optional<SemanticType> upperBound, Optional<SemanticType> defaultType) {
  public BoundTypeParameter {
    Objects.requireNonNull(type, "type");
    upperBound = Objects.requireNonNull(upperBound, "upperBound");
    defaultType = Objects.requireNonNull(defaultType, "defaultType");
  }

  public BoundTypeParameter(SemanticType type, Optional<SemanticType> upperBound) {
    this(type, upperBound, Optional.empty());
  }
}
