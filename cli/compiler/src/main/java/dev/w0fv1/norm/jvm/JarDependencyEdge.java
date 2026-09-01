package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JarDependencyEdge(JarArtifactIdentity from, JarArtifactIdentity to) {
  public JarDependencyEdge {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (from.equals(to))
      throw new IllegalArgumentException("JAR dependency cannot reference itself");
  }
}
