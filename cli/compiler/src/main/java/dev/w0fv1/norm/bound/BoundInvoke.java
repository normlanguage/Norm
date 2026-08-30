package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundInvoke(
    BoundExpression callee, List<BoundArgument> arguments, SemanticType type, SourceSpan span)
    implements BoundExpression {
  public BoundInvoke {
    Objects.requireNonNull(callee, "callee");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(span, "span");
  }
}
