package dev.w0fv1.norm.core;

import java.util.Objects;

public record BuiltinTypeId(String value) implements Comparable<BuiltinTypeId> {
  public BuiltinTypeId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) throw new IllegalArgumentException("builtin type id must not be blank");
  }

  @Override
  public int compareTo(BuiltinTypeId other) {
    return value.compareTo(Objects.requireNonNull(other, "other").value);
  }
}
