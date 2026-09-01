package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaAnnotationBinding(
    String binaryName,
    JavaAnnotationContract contract,
    List<JavaAnnotationElementBinding> elements) {
  public JavaAnnotationBinding {
    Objects.requireNonNull(binaryName, "binaryName");
    if (binaryName.isBlank()) throw new IllegalArgumentException("annotation binary name is blank");
    Objects.requireNonNull(contract, "contract");
    elements = List.copyOf(elements);
  }
}
