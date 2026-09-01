package dev.w0fv1.norm.value;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record JarBinding(JarTarget target, List<JarBindingType> api) {
  public JarBinding {
    Objects.requireNonNull(target, "target");
    api = List.copyOf(api);
    HashSet<String> unique = new HashSet<>();
    for (JarBindingType type : api) {
      Objects.requireNonNull(type, "type");
      if (!unique.add(type.name())) {
        throw new IllegalArgumentException("duplicate JAR binding type '" + type.name() + "'");
      }
    }
  }

  public JarBinding(JarTarget target) {
    this(target, List.of());
  }
}
