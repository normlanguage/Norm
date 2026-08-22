package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record BoundConstruct(
    BoundClassId target,
    BoundRuntimeType runtimeType,
    List<BoundArgument> arguments,
    SemanticType type,
    SourceSpan span)
    implements BoundExpression {
  public BoundConstruct {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(runtimeType, "runtimeType");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(span, "span");
  }
}
