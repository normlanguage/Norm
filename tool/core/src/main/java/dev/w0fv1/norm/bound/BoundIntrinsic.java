package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundIntrinsic(
    IntrinsicId intrinsic,
    Optional<BoundExpression> receiver,
    List<BoundArgument> arguments,
    Optional<BoundRuntimeType> runtimeType,
    SemanticType type,
    SourceSpan span)
    implements BoundExpression {
  public BoundIntrinsic {
    Objects.requireNonNull(intrinsic, "intrinsic");
    receiver = Objects.requireNonNull(receiver, "receiver");
    arguments = List.copyOf(arguments);
    runtimeType = Objects.requireNonNull(runtimeType, "runtimeType");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(span, "span");
  }
}
