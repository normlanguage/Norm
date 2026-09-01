package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaArrayType(JavaBindingType component) implements JavaBindingType {
  public JavaArrayType {
    Objects.requireNonNull(component, "component");
    if (component == JavaPrimitiveType.VOID) {
      throw new IllegalArgumentException("Java arrays cannot contain void");
    }
  }

  @Override
  public String descriptor() {
    return "[" + component.descriptor();
  }

  @Override
  public String displayName() {
    return component.displayName() + "[]";
  }
}
