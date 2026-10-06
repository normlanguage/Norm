package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record BuiltinTypeConformance(SemanticType concreteType, SemanticType interfaceType) {
  public BuiltinTypeConformance {
    Objects.requireNonNull(concreteType, "concreteType");
    Objects.requireNonNull(interfaceType, "interfaceType");
    if (concreteType.kind() != SemanticType.Kind.DECLARED
        || concreteType.isNullable()
        || interfaceType.kind() != SemanticType.Kind.DECLARED
        || interfaceType.isNullable()) {
      throw new IllegalArgumentException("builtin conformances require non-null declared types");
    }
  }
}
