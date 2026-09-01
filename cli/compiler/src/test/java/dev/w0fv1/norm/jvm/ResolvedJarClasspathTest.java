package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolvedJarClasspathTest {
  @TempDir Path temporaryDirectory;

  @Test
  void selectsTheExplicitRootOverAnOlderTransitiveVersion() {
    ResolvedJarArtifact core = artifact("io.micronaut", "micronaut-core", "5.1.13", "core");
    ResolvedJarArtifact serde =
        artifact("io.micronaut.serde", "micronaut-serde-api", "3.1.1", "serde");
    ResolvedJarArtifact olderCore =
        artifact("io.micronaut", "micronaut-core", "5.1.3", "older-core");

    assertEquals(
        List.of(core.file(), serde.file()),
        ResolvedJarClasspath.resolve(
            List.of(graph(core), graph(serde, olderCore, edge(serde, olderCore)))));
  }

  @Test
  void selectsTheHighestTransitiveVersionAndItsDependencyClosure() {
    ResolvedJarArtifact first = artifact("sample", "first", "1", "first");
    ResolvedJarArtifact second = artifact("sample", "second", "1", "second");
    ResolvedJarArtifact sharedOne = artifact("sample", "shared", "1.9", "shared-one");
    ResolvedJarArtifact sharedTwo = artifact("sample", "shared", "1.10", "shared-two");
    ResolvedJarArtifact oldOnly = artifact("sample", "old-only", "1", "old-only");
    ResolvedJarArtifact newOnly = artifact("sample", "new-only", "1", "new-only");

    assertEquals(
        List.of(first.file(), second.file(), sharedTwo.file(), newOnly.file()),
        ResolvedJarClasspath.resolve(
            List.of(
                graph(
                    first,
                    List.of(first, sharedOne, oldOnly),
                    List.of(edge(first, sharedOne), edge(sharedOne, oldOnly))),
                graph(
                    second,
                    List.of(second, sharedTwo, newOnly),
                    List.of(edge(second, sharedTwo), edge(sharedTwo, newOnly))))));
  }

  @Test
  void rejectsDifferentExplicitVersionsOfTheSameRootArtifact() {
    ResolvedJarArtifact first = artifact("sample", "library", "1", "first");
    ResolvedJarArtifact second = artifact("sample", "library", "2", "second");

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> ResolvedJarClasspath.resolve(List.of(graph(first), graph(second))));

    assertEquals(
        "Java classpath selects explicit roots sample:library:1 and sample:library:2",
        failure.getMessage());
  }

  @Test
  void rejectsDifferentContentForOneImmutableCoordinate() {
    ResolvedJarArtifact root = artifact("sample", "root", "1", "root");
    ResolvedJarArtifact first = artifact("sample", "library", "1", "first");
    ResolvedJarArtifact second = artifact("sample", "library", "1", "second");

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ResolvedJarClasspath.resolve(
                    List.of(
                        graph(root, first, edge(root, first)),
                        graph(root, second, edge(root, second)))));

    assertEquals(
        "Java artifact sample:library:1 resolves to different content", failure.getMessage());
  }

  private ResolvedJarArtifact artifact(
      String group, String artifact, String version, String content) {
    Path file = temporaryDirectory.resolve(content + ".jar");
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate(group, artifact, version)),
        file,
        Sha256Digest.compute(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private static JarDependencyEdge edge(ResolvedJarArtifact from, ResolvedJarArtifact to) {
    return new JarDependencyEdge(from.identity(), to.identity());
  }

  private static ResolvedJarGraph graph(ResolvedJarArtifact root) {
    return new ResolvedJarGraph(root, List.of(root), List.of());
  }

  private static ResolvedJarGraph graph(
      ResolvedJarArtifact root, ResolvedJarArtifact dependency, JarDependencyEdge edge) {
    return graph(root, List.of(root, dependency), List.of(edge));
  }

  private static ResolvedJarGraph graph(
      ResolvedJarArtifact root,
      List<ResolvedJarArtifact> artifacts,
      List<JarDependencyEdge> edges) {
    return new ResolvedJarGraph(root, artifacts, edges);
  }
}
