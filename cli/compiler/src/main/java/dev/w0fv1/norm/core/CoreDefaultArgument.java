package dev.w0fv1.norm.core;

import java.util.Objects;
import java.util.function.IntFunction;

public sealed interface CoreDefaultArgument {
  default Resolved resolve(IntFunction<DefinitionOccurrenceId> occurrences) {
    return switch (this) {
      case Pending pending -> new Resolved(occurrences.apply(pending.declarationIndex()));
      case Resolved resolved -> resolved;
    };
  }

  record Pending(int declarationIndex) implements CoreDefaultArgument {
    public Pending {
      if (declarationIndex < 0)
        throw new IllegalArgumentException("default declaration index must not be negative");
    }
  }

  record Resolved(DefinitionOccurrenceId occurrence) implements CoreDefaultArgument {
    public Resolved {
      Objects.requireNonNull(occurrence, "occurrence");
    }
  }
}
