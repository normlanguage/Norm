package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record ParameterInfo(String name, SemanticType type, boolean hasDefault) {
  public ParameterInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }

  public ParameterInfo(String name, SemanticType type) {
    this(name, type, false);
  }
}
