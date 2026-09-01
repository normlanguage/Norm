package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaClassTypeSegment(String name, List<JavaTypeArgument> arguments) {
  public JavaClassTypeSegment {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("class type segment must not be blank");
    arguments = List.copyOf(arguments);
  }
}
