package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.Objects;

public record ResolvedIteration(SemanticType elementType, Strategy strategy) {
  public ResolvedIteration {
    Objects.requireNonNull(elementType, "elementType");
    Objects.requireNonNull(strategy, "strategy");
  }

  public sealed interface Strategy permits Strategy.Builtin, Strategy.Interface {
    record Builtin(IntrinsicId intrinsic) implements Strategy {
      public Builtin {
        Objects.requireNonNull(intrinsic, "intrinsic");
      }
    }

    record Interface(
        SemanticType iterableInterfaceType,
        SymbolId iteratorRequirement,
        SemanticType iteratorInterfaceType,
        SymbolId hasNextRequirement,
        SymbolId nextRequirement)
        implements Strategy {
      public Interface {
        Objects.requireNonNull(iterableInterfaceType, "iterableInterfaceType");
        Objects.requireNonNull(iteratorRequirement, "iteratorRequirement");
        Objects.requireNonNull(iteratorInterfaceType, "iteratorInterfaceType");
        Objects.requireNonNull(hasNextRequirement, "hasNextRequirement");
        Objects.requireNonNull(nextRequirement, "nextRequirement");
      }
    }
  }
}
