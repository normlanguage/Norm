package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaTypeParameter(
    String name, Optional<JavaTypeSignature> classBound, List<JavaTypeSignature> interfaceBounds) {
  public JavaTypeParameter {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("type parameter name must not be blank");
    Objects.requireNonNull(classBound, "classBound");
    interfaceBounds = List.copyOf(interfaceBounds);
  }
}
