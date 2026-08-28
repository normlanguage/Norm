package dev.w0fv1.norm.execution;

import java.util.Objects;

public record PlatformHttpHeader(String name, String value) {
  public PlatformHttpHeader {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
  }
}
