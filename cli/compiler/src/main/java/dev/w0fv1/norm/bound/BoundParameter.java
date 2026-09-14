package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundParameter(
    BoundLocalId id,
    ParameterInfo declaration,
    int ordinal,
    List<BoundInterceptor> interceptors,
    Optional<BoundCallableId> defaultValue) {
  public BoundParameter {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(declaration, "declaration");
    if (ordinal < 0) throw new IllegalArgumentException("parameter ordinal must be non-negative");
    interceptors = List.copyOf(interceptors);
    defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    if (declaration.policy().hasDefault() != defaultValue.isPresent()) {
      throw new IllegalArgumentException("default implementation must match the parameter policy");
    }
  }

  public BoundParameter(BoundLocalId id, String name, SemanticType type, int ordinal) {
    this(id, new ParameterInfo(name, type), ordinal, List.of(), Optional.empty());
  }

  public String name() {
    return declaration.name();
  }

  public SemanticType type() {
    return declaration.type();
  }
}
