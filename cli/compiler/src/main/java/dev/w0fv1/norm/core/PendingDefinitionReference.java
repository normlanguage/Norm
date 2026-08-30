package dev.w0fv1.norm.core;

public record PendingDefinitionReference(int declarationIndex) implements CoreDefinitionLink {
  public PendingDefinitionReference {
    if (declarationIndex < 0) {
      throw new IllegalArgumentException("declaration index must not be negative");
    }
  }
}
