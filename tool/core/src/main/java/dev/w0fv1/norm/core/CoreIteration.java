package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.Objects;

public sealed interface CoreIteration permits CoreIteration.Builtin, CoreIteration.Interface {
  record Builtin(IntrinsicId intrinsic) implements CoreIteration {
    public Builtin {
      Objects.requireNonNull(intrinsic, "intrinsic");
    }
  }

  record Interface(
      CoreDefinitionLink iteratorRequirement,
      CoreDefinitionLink hasNextRequirement,
      CoreDefinitionLink nextRequirement)
      implements CoreIteration {
    public Interface {
      Objects.requireNonNull(iteratorRequirement, "iteratorRequirement");
      Objects.requireNonNull(hasNextRequirement, "hasNextRequirement");
      Objects.requireNonNull(nextRequirement, "nextRequirement");
    }
  }
}
