package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.Optional;

public record JavaBindingTypeParameter(String name, Optional<JavaBindingType> bound) {
  public JavaBindingTypeParameter {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(bound, "bound");
    if (name.isBlank()) throw new IllegalArgumentException("type parameter name must not be blank");
  }
}
