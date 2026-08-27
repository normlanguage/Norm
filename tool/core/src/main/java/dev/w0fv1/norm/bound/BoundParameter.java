package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;

public record BoundParameter(
    BoundLocalId id,
    String name,
    SemanticType type,
    int ordinal,
    List<BoundInterceptor> interceptors) {
  public BoundParameter {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (ordinal < 0) throw new IllegalArgumentException("parameter ordinal must be non-negative");
    interceptors = List.copyOf(interceptors);
  }

  public BoundParameter(BoundLocalId id, String name, SemanticType type, int ordinal) {
    this(id, name, type, ordinal, List.of());
  }
}
