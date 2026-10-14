package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CoreField(
    CoreVisibility visibility,
    String name,
    int ordinal,
    CoreType type,
    List<CoreInterceptor> interceptors,
    Optional<CoreDefinitionLink> initializer) {
  public CoreField {
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("field name must not be blank");
    if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
    Objects.requireNonNull(type, "type");
    interceptors = List.copyOf(interceptors);
    initializer = Objects.requireNonNull(initializer, "initializer");
  }

  public CoreField(
      CoreVisibility visibility,
      String name,
      int ordinal,
      CoreType type,
      List<CoreInterceptor> interceptors) {
    this(visibility, name, ordinal, type, interceptors, Optional.empty());
  }
}
