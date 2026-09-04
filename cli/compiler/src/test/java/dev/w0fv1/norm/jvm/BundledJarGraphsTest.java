package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundledJarGraphsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void preservesAResolvedGraphWithoutAMavenRepository() throws Exception {
    Path rootFile = temporaryDirectory.resolve("root.jar");
    Path dependencyFile = temporaryDirectory.resolve("dependency.jar");
    Files.writeString(rootFile, "root");
    Files.writeString(dependencyFile, "dependency");
    ResolvedJarArtifact root = artifact("org.example", "root", "1.0", rootFile);
    ResolvedJarArtifact dependency = artifact("org.example", "dependency", "2.0", dependencyFile);
    ResolvedJarGraph graph =
        new ResolvedJarGraph(
            root,
            List.of(root, dependency),
            List.of(new JarDependencyEdge(root.identity(), dependency.identity())));
    ResolvedJarBinding binding =
        new ResolvedJarBinding(
            graph,
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path bundle = temporaryDirectory.resolve("bundle");

    BundledJarGraphs.write(bundle, List.of(binding));
    ResolvedJarGraph restored = BundledJarGraphs.read(bundle).get(graph.contentId());

    assertEquals(graph.contentId(), restored.contentId());
    assertEquals(
        graph.artifacts().stream().map(ResolvedJarArtifact::identity).toList(),
        restored.artifacts().stream().map(ResolvedJarArtifact::identity).toList());
    assertEquals(graph.edges(), restored.edges());
  }

  private static ResolvedJarArtifact artifact(String group, String name, String version, Path file)
      throws Exception {
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate(group, name, version)),
        file,
        Sha256Digest.compute(file));
  }
}
