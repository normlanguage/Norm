package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaBindingTypeVariable(String name, JavaBindingType erasure)
    implements JavaBindingType {
  public JavaBindingTypeVariable {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(erasure, "erasure");
    if (name.isBlank()) throw new IllegalArgumentException("type variable name must not be blank");
    if (erasure instanceof JavaBindingTypeVariable) {
      throw new IllegalArgumentException("type variable erasure must be concrete");
    }
  }

  @Override
  public String descriptor() {
    return erasure.descriptor();
  }

  @Override
  public String displayName() {
    return name;
  }
}
