package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ResolvedJarGraph {
  private final ResolvedJarArtifact root;
  private final List<ResolvedJarArtifact> artifacts;
  private final List<JarDependencyEdge> edges;
  private final Sha256Digest contentId;

  public ResolvedJarGraph(
      ResolvedJarArtifact root,
      List<ResolvedJarArtifact> artifacts,
      List<JarDependencyEdge> edges) {
    this.root = Objects.requireNonNull(root, "root");
    this.artifacts =
        artifacts.stream()
            .sorted(Comparator.comparing(value -> value.identity().canonical()))
            .toList();
    this.edges =
        edges.stream()
            .distinct()
            .sorted(
                Comparator.comparing((JarDependencyEdge value) -> value.from().canonical())
                    .thenComparing(value -> value.to().canonical()))
            .toList();
    validate();
    contentId = identify();
  }

  public ResolvedJarArtifact root() {
    return root;
  }

  public List<ResolvedJarArtifact> artifacts() {
    return artifacts;
  }

  public List<JarDependencyEdge> edges() {
    return edges;
  }

  public Sha256Digest contentId() {
    return contentId;
  }

  private void validate() {
    Set<JarArtifactIdentity> identities = new HashSet<>();
    for (ResolvedJarArtifact artifact : artifacts) {
      if (!identities.add(artifact.identity())) {
        throw new IllegalArgumentException(
            "duplicate resolved JAR " + artifact.identity().canonical());
      }
    }
    if (!identities.contains(root.identity())) {
      throw new IllegalArgumentException("resolved JAR graph does not contain its root");
    }
    for (JarDependencyEdge edge : edges) {
      if (!identities.contains(edge.from()) || !identities.contains(edge.to())) {
        throw new IllegalArgumentException("resolved JAR edge references an unknown artifact");
      }
    }
  }

  private Sha256Digest identify() {
    if (artifacts.size() == 1 && edges.isEmpty()) return root.content();
    StringBuilder canonical = new StringBuilder();
    for (ResolvedJarArtifact artifact : artifacts) {
      canonical
          .append("artifact\0")
          .append(artifact.identity().canonical())
          .append('\0')
          .append(artifact.content().value())
          .append('\n');
    }
    for (JarDependencyEdge edge : edges) {
      canonical
          .append("edge\0")
          .append(edge.from().canonical())
          .append('\0')
          .append(edge.to().canonical())
          .append('\n');
    }
    return Sha256Digest.compute(canonical.toString().getBytes(StandardCharsets.UTF_8));
  }
}
