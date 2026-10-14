package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreFieldInitializer(int nodeIndex, CoreDefinitionLink target) implements CoreNode {
  public CoreFieldInitializer {
    if (nodeIndex < 0)
      throw new IllegalArgumentException("initializer node index must not be negative");
    Objects.requireNonNull(target, "target");
  }
}
