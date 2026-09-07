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
  void freezesVersionSelectionBeforeDerivingPurposeClosures() {
    var processor = artifact("sample", "processor", "1", "processor");
    var runtime = artifact("sample", "runtime", "1", "runtime");
    var oldShared = artifact("sample", "shared", "1", "old-shared");
    var shared = artifact("sample", "shared", "2", "shared");
    var graphs =
        new java.util.ArrayList<>(
            List.of(
                graph(processor, oldShared, edge(processor, oldShared)),
                graph(runtime, shared, edge(runtime, shared))));
    var resolved = ResolvedJarClasspath.resolve(graphs);
    graphs.clear();
    assertEquals(List.of(processor, runtime, shared), resolved.artifacts());
    assertEquals(List.of(processor, shared), resolved.closure(List.of(processor.identity())));
    assertEquals(List.of(runtime, shared), resolved.closure(List.of(runtime.identity())));
    assertEquals(List.of(shared), resolved.closure(List.of(oldShared.identity())));
    assertThrows(UnsupportedOperationException.class, () -> resolved.artifacts().clear());
    assertEquals(List.of(), resolved.closure(List.of()));
  }

  @Test
  void scopesRootsWithoutChangingVersionSelectionOrSharedDependencies() {
    var processor = artifact("sample", "processor", "1", "processor");
    var runtime = artifact("sample", "runtime", "1", "runtime");
    var oldShared = artifact("sample", "shared", "1", "old-shared");
    var shared = artifact("sample", "shared", "2", "shared");
    var helper = artifact("sample", "helper", "1", "helper");
    var graphs =
        List.of(
            graph(processor, oldShared, edge(processor, oldShared)),
            graph(runtime, shared, edge(runtime, shared)),
            graph(shared, helper, edge(shared, helper)));
    assertEquals(
        List.of(processor, shared, helper),
        ResolvedJarClasspath.artifacts(graphs, List.of(processor.identity())));
    assertEquals(
        List.of(runtime, shared, helper),
        ResolvedJarClasspath.artifacts(graphs, List.of(runtime.identity())));
    assertEquals(List.of(), ResolvedJarClasspath.artifacts(graphs, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ResolvedJarClasspath.artifacts(
                graphs, List.of(artifact("sample", "missing", "1", "missing").identity())));
  }

  @Test
  void selectsTheExplicitRootOverAnOlderTransitiveVersion() {
    ResolvedJarArtifact core = artifact("io.sample", "sample-core", "5.1.13", "core");
    ResolvedJarArtifact serde = artifact("io.sample.serde", "sample-serde-api", "3.1.1", "serde");
    ResolvedJarArtifact olderCore = artifact("io.sample", "sample-core", "5.1.3", "older-core");

    assertEquals(
        List.of(core, serde),
        ResolvedJarClasspath.artifacts(
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
        List.of(first, second, sharedTwo, newOnly),
        ResolvedJarClasspath.artifacts(
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
            () -> ResolvedJarClasspath.artifacts(List.of(graph(first), graph(second))));

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
                ResolvedJarClasspath.artifacts(
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
