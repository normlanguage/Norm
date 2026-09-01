package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaTypeVariableSignature(String name) implements JavaTypeSignature {
  public JavaTypeVariableSignature {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("type variable name must not be blank");
  }
}
