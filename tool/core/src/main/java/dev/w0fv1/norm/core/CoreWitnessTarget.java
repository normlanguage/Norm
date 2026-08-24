package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.Objects;

public sealed interface CoreWitnessTarget
    permits CoreWitnessTarget.Callable, CoreWitnessTarget.Intrinsic {
  record Callable(CoreDefinitionLink definition) implements CoreWitnessTarget {
    public Callable {
      Objects.requireNonNull(definition, "definition");
    }
  }

  record Intrinsic(IntrinsicId intrinsic) implements CoreWitnessTarget {
    public Intrinsic {
      Objects.requireNonNull(intrinsic, "intrinsic");
    }
  }
}
