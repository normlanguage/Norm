package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreField(int ordinal, CoreType type) {
  public CoreField {
    if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
    Objects.requireNonNull(type, "type");
  }
}
