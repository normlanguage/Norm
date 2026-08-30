package dev.w0fv1.norm.core;

import java.util.List;

public record CoreBlock(int nodeIndex, List<CoreStatement> statements) implements CoreNode {
  public CoreBlock {
    if (nodeIndex < 0) throw new IllegalArgumentException("node index must not be negative");
    statements = List.copyOf(statements);
  }
}
