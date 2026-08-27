package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreField(
    String name, int ordinal, CoreType type, List<CoreInterceptor> interceptors) {
  public CoreField {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("field name must not be blank");
    if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
    Objects.requireNonNull(type, "type");
    interceptors = List.copyOf(interceptors);
  }
}
