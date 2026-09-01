package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record ResolvedJarBinding(
    ResolvedJarGraph graph, JarApiSchema api, GeneratedJarBinding generated) {
  public ResolvedJarBinding {
    Objects.requireNonNull(graph, "graph");
    Objects.requireNonNull(api, "api");
    Objects.requireNonNull(generated, "generated");
  }
}
