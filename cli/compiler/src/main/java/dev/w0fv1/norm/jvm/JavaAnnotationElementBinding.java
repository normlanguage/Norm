package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.Optional;

public record JavaAnnotationElementBinding(
    String name,
    String descriptor,
    JavaBindingType type,
    Optional<JavaAnnotationValue> defaultValue) {
  public JavaAnnotationElementBinding {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("annotation element name is blank");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(defaultValue, "defaultValue");
  }
}
