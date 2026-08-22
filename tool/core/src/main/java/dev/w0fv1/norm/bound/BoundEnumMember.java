package dev.w0fv1.norm.bound;

import java.util.Objects;

public record BoundEnumMember(BoundEnumMemberId id, String name, int ordinal) {
  public BoundEnumMember {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    if (ordinal < 0) throw new IllegalArgumentException("enum ordinal must be non-negative");
  }
}
