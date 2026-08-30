package dev.w0fv1.norm.core;

import java.util.Objects;

public sealed interface DefinitionReference extends CoreDefinitionLink
    permits DefinitionReference.External, DefinitionReference.RecursiveMember {
  record External(DefinitionId definition) implements DefinitionReference {
    public External {
      Objects.requireNonNull(definition, "definition");
    }
  }

  record RecursiveMember(int memberIndex) implements DefinitionReference {
    public RecursiveMember {
      if (memberIndex < 0) {
        throw new IllegalArgumentException("recursive member index must not be negative");
      }
    }
  }
}
