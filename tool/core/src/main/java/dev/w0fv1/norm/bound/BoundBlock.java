package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundBlock(List<BoundStatement> statements, SourceSpan span) implements BoundNode {
  public BoundBlock {
    statements = List.copyOf(statements);
    Objects.requireNonNull(span, "span");
  }
}
