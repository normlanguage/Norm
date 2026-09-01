package dev.w0fv1.norm.execution;

import java.util.Objects;

public record JarBindingEnumValue(JarBindingClassReference.Nominal type, String variant) {
  public JarBindingEnumValue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(variant, "variant");
    if (variant.isBlank()) throw new IllegalArgumentException("enum variant must not be blank");
  }
}
