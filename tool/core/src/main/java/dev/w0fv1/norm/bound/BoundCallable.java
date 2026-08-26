package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundCallable(
    BoundCallableId id,
    String name,
    BoundVisibility visibility,
    Optional<BoundAggregateId> owner,
    Optional<SemanticType> receiverType,
    Optional<BoundLocalId> thisLocal,
    List<BoundParameter> captures,
    List<BoundParameter> parameters,
    List<BoundTypeParameter> typeParameters,
    List<BoundReifiedArgument> reifiedParameters,
    SemanticType returnType,
    BoundBlock body,
    SourceSpan span)
    implements BoundNode {
  public BoundCallable {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(visibility, "visibility");
    owner = Objects.requireNonNull(owner, "owner");
    receiverType = Objects.requireNonNull(receiverType, "receiverType");
    thisLocal = Objects.requireNonNull(thisLocal, "thisLocal");
    if ((owner.isPresent() || receiverType.isPresent()) != thisLocal.isPresent()) {
      throw new IllegalArgumentException(
          "callable receiver and this local must be present together");
    }
    parameters = List.copyOf(parameters);
    captures = List.copyOf(captures);
    typeParameters = List.copyOf(typeParameters);
    reifiedParameters = List.copyOf(reifiedParameters);
    Objects.requireNonNull(returnType, "returnType");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(span, "span");
  }

  public BoundCallable(
      BoundCallableId id,
      String name,
      BoundVisibility visibility,
      Optional<BoundAggregateId> owner,
      Optional<BoundLocalId> thisLocal,
      List<BoundParameter> parameters,
      List<BoundTypeParameter> typeParameters,
      List<BoundReifiedArgument> reifiedParameters,
      SemanticType returnType,
      BoundBlock body,
      SourceSpan span) {
    this(
        id,
        name,
        visibility,
        owner,
        Optional.empty(),
        thisLocal,
        List.of(),
        parameters,
        typeParameters,
        reifiedParameters,
        returnType,
        body,
        span);
  }
}
