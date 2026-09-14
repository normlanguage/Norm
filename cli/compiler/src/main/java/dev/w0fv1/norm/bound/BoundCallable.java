package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundCallable(
    BoundCallableId id,
    BoundCallableKind kind,
    String name,
    BoundVisibility visibility,
    Optional<BoundAggregateId> owner,
    Optional<SemanticType> receiverType,
    Optional<BoundLocalId> thisLocal,
    List<BoundParameter> captures,
    List<BoundParameter> parameters,
    List<BoundTypeParameter> typeParameters,
    List<BoundReifiedArgument> reifiedParameters,
    List<BoundInterceptor> interceptors,
    SemanticType returnType,
    Optional<BoundBlock> implementation,
    SourceSpan span)
    implements BoundNode {
  public BoundCallable {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    owner = Objects.requireNonNull(owner, "owner");
    receiverType = Objects.requireNonNull(receiverType, "receiverType");
    thisLocal = Objects.requireNonNull(thisLocal, "thisLocal");
    if (receiverType.isPresent() != thisLocal.isPresent()) {
      throw new IllegalArgumentException(
          "callable receiver and this local must be present together");
    }
    boolean receiverExpected =
        kind == BoundCallableKind.CONSTRUCTOR || kind == BoundCallableKind.METHOD;
    if (receiverExpected != receiverType.isPresent()) {
      throw new IllegalArgumentException("callable kind does not match receiver presence");
    }
    if (kind == BoundCallableKind.CONSTRUCTOR && owner.isEmpty()) {
      throw new IllegalArgumentException("constructor must have an owner");
    }
    if ((kind == BoundCallableKind.FUNCTION
            || kind == BoundCallableKind.EXTENSION
            || kind == BoundCallableKind.DEFAULT_ARGUMENT
            || kind == BoundCallableKind.LAMBDA)
        && owner.isPresent()) {
      throw new IllegalArgumentException("function and lambda cannot have an owner");
    }
    parameters = List.copyOf(parameters);
    captures = List.copyOf(captures);
    typeParameters = List.copyOf(typeParameters);
    reifiedParameters = List.copyOf(reifiedParameters);
    interceptors = List.copyOf(interceptors);
    Objects.requireNonNull(returnType, "returnType");
    implementation = Objects.requireNonNull(implementation, "implementation");
    if (implementation.isEmpty() && (kind != BoundCallableKind.METHOD || owner.isEmpty())) {
      throw new IllegalArgumentException("only owned methods can omit their implementation");
    }
    Objects.requireNonNull(span, "span");
  }

  public BoundCallable(
      BoundCallableId id,
      BoundCallableKind kind,
      String name,
      BoundVisibility visibility,
      Optional<BoundAggregateId> owner,
      Optional<SemanticType> receiverType,
      Optional<BoundLocalId> thisLocal,
      List<BoundParameter> captures,
      List<BoundParameter> parameters,
      List<BoundTypeParameter> typeParameters,
      List<BoundReifiedArgument> reifiedParameters,
      List<BoundInterceptor> interceptors,
      SemanticType returnType,
      BoundBlock body,
      SourceSpan span) {
    this(
        id,
        kind,
        name,
        visibility,
        owner,
        receiverType,
        thisLocal,
        captures,
        parameters,
        typeParameters,
        reifiedParameters,
        interceptors,
        returnType,
        Optional.of(body),
        span);
  }

  public BoundBlock body() {
    return implementation.orElseThrow(
        () -> new IllegalStateException("method declaration has no executable body: " + name));
  }
}
