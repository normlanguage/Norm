package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreCallableParameter(
    String name, CoreType type, int localIndex, List<CoreInterceptor> interceptors) {
  public CoreCallableParameter {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("parameter name must not be blank");
    Objects.requireNonNull(type, "type");
    if (localIndex < 0) throw new IllegalArgumentException("parameter local must not be negative");
    interceptors = List.copyOf(interceptors);
  }
}
