package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.Objects;

public sealed interface JarBindingClassReference
    permits JarBindingClassReference.Builtin, JarBindingClassReference.Nominal {
  record Builtin(String typeId) implements JarBindingClassReference {
    public Builtin {
      Objects.requireNonNull(typeId, "typeId");
      if (typeId.isBlank()) throw new IllegalArgumentException("builtin type id must not be blank");
    }
  }

  record Nominal(ModuleCoordinate module, String packageName, String name)
      implements JarBindingClassReference {
    public Nominal {
      Objects.requireNonNull(module, "module");
      Objects.requireNonNull(packageName, "packageName");
      Objects.requireNonNull(name, "name");
      if (name.isBlank()) throw new IllegalArgumentException("type name must not be blank");
    }
  }
}
