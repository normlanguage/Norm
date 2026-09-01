package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JarResolverTest {
  @TempDir Path temporaryDirectory;

  @Test
  void resolvesAndIdentifiesALocalJarByItsBytes() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("sample"));
    Path jar = createJar(moduleRoot.resolve("lib/sample.jar"), "sample/Value.class", "class-bytes");

    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("cache"))) {
      ResolvedJarGraph graph =
          resolver.resolve(
              moduleRoot, new JarBinding(new LocalJarTarget("lib/sample.jar", Optional.empty())));

      assertEquals(jar.toAbsolutePath().normalize(), graph.root().file());
      assertEquals(1, graph.artifacts().size());
      assertTrue(graph.edges().isEmpty());
      assertEquals(graph.root().content(), graph.contentId());
    }
  }

  @Test
  void rejectsChangedLocalJarContent() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("changed"));
    createJar(moduleRoot.resolve("lib/sample.jar"), "sample/Value.class", "new-content");
    var target =
        new LocalJarTarget(
            "lib/sample.jar",
            Optional.of(dev.w0fv1.norm.value.Sha256Digest.parse("0123456789abcdef".repeat(4))));

    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("cache"))) {
      IOException exception =
          assertThrows(
              IOException.class, () -> resolver.resolve(moduleRoot, new JarBinding(target)));

      assertTrue(exception.getMessage().contains("integrity"));
    }
  }

  @Test
  void resolvesApacheCommonsLangFromMavenCentral() throws Exception {
    var coordinate = new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0");

    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("maven-cache"))) {
      ResolvedJarGraph graph =
          resolver.resolve(
              temporaryDirectory, new JarBinding(new MavenJarTarget(coordinate, Optional.empty())));

      assertEquals(new MavenJarIdentity(coordinate), graph.root().identity());
      assertTrue(Files.isRegularFile(graph.root().file()));
      assertEquals(1, graph.artifacts().size());
      assertEquals(64, graph.contentId().value().length());
    }
  }

  @Test
  void excludesOptionalDependenciesFromTheConsumerRuntimeGraph() throws Exception {
    Path repository = temporaryDirectory.resolve("optional-repository");
    Path root = Files.createDirectories(repository.resolve("test/root/1"));
    Path optional = Files.createDirectories(repository.resolve("test/optional/1"));
    createJar(root.resolve("root-1.jar"), "test/Root.class", "root");
    createJar(optional.resolve("optional-1.jar"), "test/Optional.class", "optional");
    Files.writeString(
        root.resolve("root-1.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>test</groupId>
          <artifactId>root</artifactId>
          <version>1</version>
          <dependencies>
            <dependency>
              <groupId>test</groupId>
              <artifactId>optional</artifactId>
              <version>1</version>
              <optional>true</optional>
            </dependency>
          </dependencies>
        </project>
        """);
    Files.writeString(
        optional.resolve("optional-1.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>test</groupId>
          <artifactId>optional</artifactId>
          <version>1</version>
        </project>
        """);
    var coordinate = new MavenArtifactCoordinate("test", "root", "1");

    try (JarResolver resolver = new JarResolver(repository)) {
      ResolvedJarGraph graph =
          resolver.resolve(
              temporaryDirectory, new JarBinding(new MavenJarTarget(coordinate, Optional.empty())));

      assertEquals(1, graph.artifacts().size());
      assertTrue(graph.edges().isEmpty());
      assertEquals(graph.root().content(), graph.contentId());
    }
  }

  private static Path createJar(Path path, String entryName, String content) throws IOException {
    Files.createDirectories(path.getParent());
    try (var output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry(entryName));
      output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return path;
  }
}
