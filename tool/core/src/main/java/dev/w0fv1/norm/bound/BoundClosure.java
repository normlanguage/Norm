package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundClosure(
    BoundCallableId target,
    Optional<BoundExpression> receiver,
    List<BoundExpression> captures,
    List<BoundRuntimeType> reifiedArguments,
    List<BoundRuntimeType> receiverTypeArguments,
    boolean virtual,
    SemanticType type,
    SourceSpan span)
    implements BoundExpression {
  public BoundClosure {
    Objects.requireNonNull(target, "target");
    receiver = Objects.requireNonNull(receiver, "receiver");
    captures = List.copyOf(captures);
    reifiedArguments = List.copyOf(reifiedArguments);
    receiverTypeArguments = List.copyOf(receiverTypeArguments);
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(span, "span");
  }

  public BoundClosure(
      BoundCallableId target,
      Optional<BoundExpression> receiver,
      List<BoundExpression> captures,
      List<BoundRuntimeType> reifiedArguments,
      SemanticType type,
      SourceSpan span) {
    this(target, receiver, captures, reifiedArguments, List.of(), false, type, span);
  }
}
