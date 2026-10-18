package dev.w0fv1.norm.execution;

import java.util.List;
import java.util.Objects;

public record RuntimeChangeSet(List<RuntimeDeclarationChange> declarations) {
  public RuntimeChangeSet {
    declarations = List.copyOf(Objects.requireNonNull(declarations, "declarations"));
  }

  public static RuntimeChangeSet empty() {
    return new RuntimeChangeSet(List.of());
  }

  public boolean isEmpty() {
    return declarations.isEmpty();
  }
}
