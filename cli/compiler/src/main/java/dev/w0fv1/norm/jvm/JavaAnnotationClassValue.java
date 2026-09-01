package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaAnnotationClassValue(String descriptor) implements JavaAnnotationValue {
  public JavaAnnotationClassValue {
    Objects.requireNonNull(descriptor, "descriptor");
  }
}
