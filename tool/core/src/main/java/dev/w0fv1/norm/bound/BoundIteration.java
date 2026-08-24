package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.Objects;

public sealed interface BoundIteration permits BoundIteration.Builtin, BoundIteration.Interface {
  record Builtin(IntrinsicId intrinsic) implements BoundIteration {
    public Builtin {
      Objects.requireNonNull(intrinsic, "intrinsic");
    }
  }

  record Interface(
      SemanticType iterableInterfaceType,
      BoundInterfaceMethodId iteratorRequirement,
      SemanticType iteratorInterfaceType,
      BoundInterfaceMethodId hasNextRequirement,
      BoundInterfaceMethodId nextRequirement)
      implements BoundIteration {
    public Interface {
      Objects.requireNonNull(iterableInterfaceType, "iterableInterfaceType");
      Objects.requireNonNull(iteratorRequirement, "iteratorRequirement");
      Objects.requireNonNull(iteratorInterfaceType, "iteratorInterfaceType");
      Objects.requireNonNull(hasNextRequirement, "hasNextRequirement");
      Objects.requireNonNull(nextRequirement, "nextRequirement");
    }
  }
}
