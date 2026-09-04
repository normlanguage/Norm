package dev.w0fv1.norm.semantic;

import java.util.Objects;
import java.util.Optional;

public record TypeParameterInfo(
    String name,
    SemanticType type,
    Optional<SemanticType> upperBound,
    Optional<SemanticType> defaultType) {
  public TypeParameterInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    upperBound = Objects.requireNonNull(upperBound, "upperBound");
    defaultType = Objects.requireNonNull(defaultType, "defaultType");
  }

  public TypeParameterInfo(String name, SemanticType type, Optional<SemanticType> upperBound) {
    this(name, type, upperBound, Optional.empty());
  }

  public TypeParameterInfo(String name, SemanticType type) {
    this(name, type, Optional.empty(), Optional.empty());
  }
}
