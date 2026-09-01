package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaClassSignature(
    List<JavaTypeParameter> typeParameters,
    Optional<JavaClassTypeSignature> superclass,
    List<JavaClassTypeSignature> interfaces) {
  public JavaClassSignature {
    typeParameters = List.copyOf(typeParameters);
    Objects.requireNonNull(superclass, "superclass");
    interfaces = List.copyOf(interfaces);
  }
}
