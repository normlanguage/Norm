package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundIntrinsic(
    IntrinsicId intrinsic,
    Optional<BoundExpression> receiver,
    List<BoundArgument> arguments,
    Optional<BoundRuntimeType> runtimeType,
    List<SemanticType> runtimeDependencies,
    boolean nullSafe,
    SemanticType type,
    SourceSpan span)
    implements BoundExpression {
  public BoundIntrinsic {
    Objects.requireNonNull(intrinsic, "intrinsic");
    receiver = Objects.requireNonNull(receiver, "receiver");
    arguments = List.copyOf(arguments);
    runtimeType = Objects.requireNonNull(runtimeType, "runtimeType");
    runtimeDependencies = List.copyOf(runtimeDependencies);
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(span, "span");
  }

  public BoundIntrinsic(
      IntrinsicId intrinsic,
      Optional<BoundExpression> receiver,
      List<BoundArgument> arguments,
      Optional<BoundRuntimeType> runtimeType,
      boolean nullSafe,
      SemanticType type,
      SourceSpan span) {
    this(
        intrinsic,
        receiver,
        arguments,
        runtimeType,
        switch (intrinsic) {
          case JAR_INVOKE, JAR_INVOKE_VOID, CONSOLE_READ, CONSOLE_WRITE ->
              List.of(SemanticType.EXCEPTION);
          default -> List.of();
        },
        nullSafe,
        type,
        span);
  }

  public BoundIntrinsic(
      IntrinsicId intrinsic,
      Optional<BoundExpression> receiver,
      List<BoundArgument> arguments,
      Optional<BoundRuntimeType> runtimeType,
      SemanticType type,
      SourceSpan span) {
    this(intrinsic, receiver, arguments, runtimeType, false, type, span);
  }
}
