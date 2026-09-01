package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaArrayTypeSignature(JavaTypeSignature component) implements JavaTypeSignature {
  public JavaArrayTypeSignature {
    Objects.requireNonNull(component, "component");
    if (component instanceof JavaPrimitiveTypeSignature primitive
        && primitive.type() == JavaPrimitiveType.VOID) {
      throw new IllegalArgumentException("Java arrays cannot contain void");
    }
  }
}
