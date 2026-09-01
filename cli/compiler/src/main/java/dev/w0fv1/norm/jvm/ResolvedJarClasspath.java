package dev.w0fv1.norm.jvm;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.aether.util.version.GenericVersionScheme;

final class ResolvedJarClasspath {
  private static final GenericVersionScheme VERSIONS = new GenericVersionScheme();

  private ResolvedJarClasspath() {}

  static List<Path> resolve(List<ResolvedJarGraph> graphs) {
    Objects.requireNonNull(graphs, "graphs");
    Map<ArtifactKey, List<Candidate>> candidates = new LinkedHashMap<>();
    List<ArtifactKey> roots = new ArrayList<>();
    for (ResolvedJarGraph graph : graphs) {
      ArtifactKey root = ArtifactKey.from(graph.root().identity());
      if (!roots.contains(root)) roots.add(root);
      for (ResolvedJarArtifact artifact : graph.artifacts()) {
        ArtifactKey key = ArtifactKey.from(artifact.identity());
        candidates
            .computeIfAbsent(key, ignored -> new ArrayList<>())
            .add(new Candidate(artifact, artifact.identity().equals(graph.root().identity())));
      }
    }
    Map<ArtifactKey, ResolvedJarArtifact> selected = new LinkedHashMap<>();
    candidates.forEach((key, values) -> selected.put(key, select(key, values)));
    Map<ArtifactKey, Set<ArtifactKey>> dependencies = dependencies(graphs, selected);
    LinkedHashSet<ArtifactKey> reachable = new LinkedHashSet<>(roots);
    ArrayDeque<ArtifactKey> pending = new ArrayDeque<>(roots);
    while (!pending.isEmpty()) {
      for (ArtifactKey dependency : dependencies.getOrDefault(pending.removeFirst(), Set.of())) {
        if (reachable.add(dependency)) pending.addLast(dependency);
      }
    }
    return reachable.stream().map(selected::get).map(ResolvedJarArtifact::file).toList();
  }

  private static ResolvedJarArtifact select(ArtifactKey key, List<Candidate> candidates) {
    requireConsistentContent(candidates);
    List<Candidate> roots = candidates.stream().filter(Candidate::root).toList();
    List<String> rootVersions =
        roots.stream()
            .map(Candidate::artifact)
            .map(ResolvedJarArtifact::identity)
            .filter(MavenJarIdentity.class::isInstance)
            .map(MavenJarIdentity.class::cast)
            .map(identity -> identity.coordinate().version())
            .distinct()
            .toList();
    if (rootVersions.size() > 1) {
      throw new IllegalArgumentException(
          "Java classpath selects explicit roots "
              + key.display()
              + ":"
              + rootVersions.get(0)
              + " and "
              + key.display()
              + ":"
              + rootVersions.get(1));
    }
    if (!roots.isEmpty()) return roots.getFirst().artifact();
    return candidates.stream()
        .map(Candidate::artifact)
        .max(Comparator.comparing(ResolvedJarClasspath::version))
        .orElseThrow();
  }

  private static void requireConsistentContent(List<Candidate> candidates) {
    Map<JarArtifactIdentity, ResolvedJarArtifact> artifacts = new LinkedHashMap<>();
    for (Candidate candidate : candidates) {
      ResolvedJarArtifact previous =
          artifacts.putIfAbsent(candidate.artifact().identity(), candidate.artifact());
      if (previous != null && !previous.content().equals(candidate.artifact().content())) {
        JarArtifactIdentity identity = candidate.artifact().identity();
        throw new IllegalArgumentException(
            "Java artifact "
                + (identity instanceof MavenJarIdentity maven
                    ? maven.coordinate().notation()
                    : identity.canonical())
                + " resolves to different content");
      }
    }
  }

  private static org.eclipse.aether.version.Version version(ResolvedJarArtifact artifact) {
    if (!(artifact.identity() instanceof MavenJarIdentity identity)) {
      throw new IllegalArgumentException("local Java artifacts cannot have version conflicts");
    }
    try {
      return VERSIONS.parseVersion(identity.coordinate().version());
    } catch (org.eclipse.aether.version.InvalidVersionSpecificationException exception) {
      throw new IllegalArgumentException(
          "invalid Java artifact version " + identity.coordinate().notation(), exception);
    }
  }

  private static Map<ArtifactKey, Set<ArtifactKey>> dependencies(
      List<ResolvedJarGraph> graphs, Map<ArtifactKey, ResolvedJarArtifact> selected) {
    Map<ArtifactKey, Set<ArtifactKey>> result = new LinkedHashMap<>();
    for (ResolvedJarGraph graph : graphs) {
      Map<JarArtifactIdentity, ResolvedJarArtifact> artifacts =
          graph.artifacts().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      ResolvedJarArtifact::identity,
                      java.util.function.Function.identity(),
                      (left, right) -> left,
                      LinkedHashMap::new));
      for (JarDependencyEdge edge : graph.edges()) {
        ArtifactKey from = ArtifactKey.from(edge.from());
        ResolvedJarArtifact source = artifacts.get(edge.from());
        if (!selected.get(from).identity().equals(source.identity())
            || !selected.get(from).content().equals(source.content())) continue;
        result
            .computeIfAbsent(from, ignored -> new LinkedHashSet<>())
            .add(ArtifactKey.from(edge.to()));
      }
    }
    return result;
  }

  private record Candidate(ResolvedJarArtifact artifact, boolean root) {}

  private record ArtifactKey(String group, String artifact, String classifier, String local) {
    private static ArtifactKey from(JarArtifactIdentity identity) {
      if (identity instanceof MavenJarIdentity maven) {
        return new ArtifactKey(
            maven.coordinate().group(), maven.coordinate().artifact(), maven.classifier(), "");
      }
      return new ArtifactKey("", "", "", identity.canonical());
    }

    private String display() {
      if (!local.isEmpty()) return local;
      return group + ":" + artifact + (classifier.isEmpty() ? "" : ":jar:" + classifier);
    }
  }
}
