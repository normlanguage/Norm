package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import java.util.Map;
import java.util.Objects;

public record ResolvedJarBinding(
    ResolvedJarGraph graph,
    JarApiSchema api,
    GeneratedJarBinding generated,
    Map<String, JarBindingClassReference.Nominal> imports) {
  public ResolvedJarBinding(
      ResolvedJarGraph graph, JarApiSchema api, GeneratedJarBinding generated) {
    this(graph, api, generated, Map.of());
  }

  public ResolvedJarBinding {
    imports = Map.copyOf(imports);
    Objects.requireNonNull(graph, "graph");
    Objects.requireNonNull(api, "api");
    Objects.requireNonNull(generated, "generated");
  }
}
