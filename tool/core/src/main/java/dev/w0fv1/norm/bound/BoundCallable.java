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
    Optional<BoundClassId> owner,
    Optional<BoundLocalId> thisLocal,
    List<BoundParameter> parameters,
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
    thisLocal = Objects.requireNonNull(thisLocal, "thisLocal");
    if (owner.isPresent() != thisLocal.isPresent()) {
      throw new IllegalArgumentException("method owner and this local must be present together");
    }
    parameters = List.copyOf(parameters);
    reifiedParameters = List.copyOf(reifiedParameters);
    Objects.requireNonNull(returnType, "returnType");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(span, "span");
  }
}
