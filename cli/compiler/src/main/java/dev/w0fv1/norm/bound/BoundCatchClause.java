package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;

public record BoundCatchClause(
    SemanticType type, BoundLocalId local, BoundBlock body, SourceSpan span) implements BoundNode {
  public BoundCatchClause {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(local, "local");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(span, "span");
  }
}
