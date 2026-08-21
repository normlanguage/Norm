package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record ParameterInfo(String name, SemanticType type) {
  public ParameterInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }
}
