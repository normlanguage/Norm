package dev.w0fv1.norm.core;

import java.util.Objects;

public sealed interface CoreTypeConstructor
    permits CoreTypeConstructor.Builtin, CoreTypeConstructor.User {
  record Builtin(BuiltinTypeId id) implements CoreTypeConstructor {
    public Builtin {
      Objects.requireNonNull(id, "id");
    }
  }

  record User(CoreDefinitionLink definition) implements CoreTypeConstructor {
    public User {
      Objects.requireNonNull(definition, "definition");
    }
  }
}
