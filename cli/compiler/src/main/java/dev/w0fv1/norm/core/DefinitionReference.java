package dev.w0fv1.norm.core;

import java.util.Objects;

public sealed interface DefinitionReference extends CoreDefinitionLink
    permits DefinitionReference.External, DefinitionReference.RecursiveMember {
  default DefinitionId resolve(
      DefinitionId owner, java.util.function.Predicate<DefinitionId> contains) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(contains, "contains");
    return switch (this) {
      case External external -> external.definition();
      case RecursiveMember recursive -> {
        var target = new DefinitionId(owner.group(), recursive.memberIndex());
        if (!contains.test(target)) {
          throw new IllegalArgumentException("recursive definition reference is outside its group");
        }
        yield target;
      }
    };
  }

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
