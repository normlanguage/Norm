package dev.w0fv1.norm.execution;

import java.util.Objects;

public record JarBindingUri(String value) {
  public JarBindingUri {
    Objects.requireNonNull(value, "value");
  }
}
