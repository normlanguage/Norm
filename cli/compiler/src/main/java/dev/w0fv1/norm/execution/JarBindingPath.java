package dev.w0fv1.norm.execution;

import java.util.Objects;

public record JarBindingPath(String value) {
  public JarBindingPath {
    Objects.requireNonNull(value, "value");
  }
}
