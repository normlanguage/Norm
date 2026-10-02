package dev.w0fv1.norm.value;

import java.util.Objects;
import java.util.Optional;

public record JdkModuleTarget(String name, Optional<Sha256Digest> resolution) implements JarTarget {
  public JdkModuleTarget {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(resolution, "resolution");
    if (!name.matches("[A-Za-z][A-Za-z0-9_.]*")) {
      throw new IllegalArgumentException("invalid JDK module name: " + name);
    }
  }
}
