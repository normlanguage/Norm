package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationStub(String binaryName, String source) {
  public JavaAnnotationStub {
    Objects.requireNonNull(binaryName, "binaryName");
    if (binaryName.isBlank()) throw new IllegalArgumentException("stub binary name is blank");
    Objects.requireNonNull(source, "source");
  }
}
