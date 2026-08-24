package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.Objects;

public record BoundWitness(BoundInterfaceMethodId requirement, Target implementation) {
  public BoundWitness {
    Objects.requireNonNull(requirement, "requirement");
    Objects.requireNonNull(implementation, "implementation");
  }

  public sealed interface Target permits Target.Callable, Target.Intrinsic {
    record Callable(BoundCallableId target) implements Target {
      public Callable {
        Objects.requireNonNull(target, "target");
      }
    }

    record Intrinsic(IntrinsicId target) implements Target {
      public Intrinsic {
        Objects.requireNonNull(target, "target");
      }
    }
  }
}
