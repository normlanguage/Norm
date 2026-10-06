package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaApiScanInput(ResolvedJarGraph graph, List<ResolvedJarGraph> supportingGraphs) {
  public JavaApiScanInput {
    Objects.requireNonNull(graph, "graph");
    supportingGraphs =
        supportingGraphs.stream()
            .filter(support -> !support.contentId().equals(graph.contentId()))
            .collect(
                java.util.stream.Collectors.toMap(
                    ResolvedJarGraph::contentId,
                    java.util.function.Function.identity(),
                    (left, right) -> left))
            .values()
            .stream()
            .sorted(java.util.Comparator.comparing(support -> support.contentId().value()))
            .toList();
  }

  public List<ResolvedJarArtifact> artifacts() {
    return java.util.stream.Stream.concat(
            graph.artifacts().stream(),
            supportingGraphs.stream().flatMap(support -> support.artifacts().stream()))
        .distinct()
        .toList();
  }
}
