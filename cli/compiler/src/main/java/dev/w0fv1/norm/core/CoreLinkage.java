package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public record CoreLinkage(List<Integer> declarations) {
  public CoreLinkage {
    declarations = List.copyOf(declarations);
    if (declarations.stream().anyMatch(index -> index < 0))
      throw new IllegalArgumentException("linkage declarations must not be negative");
  }

  public CoreLinkage relocate(IntUnaryOperator indices) {
    Objects.requireNonNull(indices, "indices");
    return new CoreLinkage(declarations.stream().map(indices::applyAsInt).toList());
  }
}
