package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaPrimitiveTypeSignature(JavaPrimitiveType type) implements JavaTypeSignature {
  public JavaPrimitiveTypeSignature {
    Objects.requireNonNull(type, "type");
  }
}
