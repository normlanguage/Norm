package dev.w0fv1.norm.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.junit.jupiter.api.Test;

final class MavenToolchainGraphTest {
  @Test
  void retainsResolvedEdgesAndPurposeRoots() {
    var root = node("dev.w0fv1.norm", "compiler", "0.23.0-SNAPSHOT");
    var execution = node("example", "execution", "1");
    var hosted = node("example", "hosted", "2");
    var tooling = node("example", "tooling", "4");
    var transitive = node("example", "transitive", "3");
    transitive.setChildren(List.of());
    hosted.setChildren(List.of());
    tooling.setChildren(List.of());
    execution.setChildren(List.of(transitive));
    root.setChildren(List.of(execution, hosted, tooling));

    var graph =
        MavenToolchainGraph.from(root, List.of("example:execution"), List.of("example:hosted"));

    assertEquals(
        List.of("example:execution:1", "example:hosted:2", "example:tooling:4"), graph.roots());
    assertEquals(List.of("example:transitive:3"), graph.dependencies().get("example:execution:1"));
    assertEquals(List.of("example:execution:1"), graph.purposes().get("execution"));
    assertEquals(List.of("example:hosted:2"), graph.purposes().get("hosted"));
    assertEquals(List.of("example:tooling:4"), graph.purposes().get("tooling"));
  }

  private static DefaultDependencyNode node(String group, String artifact, String version) {
    Artifact value =
        new DefaultArtifact(
            group,
            artifact,
            version,
            Artifact.SCOPE_COMPILE,
            "jar",
            null,
            new DefaultArtifactHandler("jar"));
    return new DefaultDependencyNode(value);
  }
}
