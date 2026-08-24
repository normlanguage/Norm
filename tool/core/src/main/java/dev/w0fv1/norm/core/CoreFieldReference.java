package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreFieldReference(CoreDefinitionLink owner, int ordinal) {
  public CoreFieldReference {
    Objects.requireNonNull(owner, "owner");
    if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
  }
}
