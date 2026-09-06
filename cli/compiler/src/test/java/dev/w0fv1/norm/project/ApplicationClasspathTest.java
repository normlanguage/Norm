package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApplicationClasspathTest {
  @TempDir Path directory;

  @Test
  void retainsTheCallerSupportGraphInTheCompiledApplication() throws Exception {
    Path jar = directory.resolve("support.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {}
    var support =
        new ResolvedJarArtifact(
            new MavenJarIdentity(new MavenArtifactCoordinate("sample", "support", "1")),
            jar,
            Sha256Digest.compute(jar));
    var graph = new ResolvedJarGraph(support, List.of(support), List.of());
    Path entry =
        Files.writeString(directory.resolve("main.norm"), "Void main() { printLine(\"linked\") }");
    try (var project = ProjectEnvironment.bootstrap(new NormRuntime()).persistentLauncher()) {
      var compilation = project.compileApplication(entry, message -> {}, List.of(graph));
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      assertEquals(List.of(support), compilation.javaClasspath().artifacts());
    }
  }
}
