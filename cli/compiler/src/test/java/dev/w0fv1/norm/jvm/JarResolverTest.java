package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.testing.MavenTestRepository;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
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
  void reusesPinnedGraphsAcrossResolversAndRejectsChangedArtifacts() throws Exception {
    Path repository = temporaryDirectory.resolve("pinned");
    Path directory = Files.createDirectories(repository.resolve("test/pinned/1"));
    Path jar = createJar(directory.resolve("pinned-1.jar"), "test/Value.class", "original");
    Files.writeString(
        directory.resolve("pinned-1.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>pinned</artifactId><version>1</version></project>
        """);
    var coordinate = new MavenArtifactCoordinate("test", "pinned", "1");
    ResolvedJarGraph graph;
    try (var resolver = new JarResolver(repository)) {
      graph =
          resolver.resolve(
              temporaryDirectory, new JarBinding(new MavenJarTarget(coordinate, Optional.empty())));
    }
    var binding = new JarBinding(new MavenJarTarget(coordinate, Optional.of(graph.contentId())));
    var messages = new java.util.ArrayList<String>();
    try (var resolver = new JarResolver(repository, messages::add)) {
      assertEquals(graph.contentId(), resolver.resolve(temporaryDirectory, binding).contentId());
    }
    assertTrue(
        messages.stream().anyMatch(message -> message.startsWith("Reused Java dependency graph")));
    Files.writeString(jar, "changed");
    try (var resolver = new JarResolver(repository)) {
      assertThrows(IOException.class, () -> resolver.resolve(temporaryDirectory, binding));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void resolvesDependenciesWithoutProbingInheritedRepositories(boolean missingChild)
      throws Exception {
    var requests = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var progress = new java.util.concurrent.CopyOnWriteArrayList<String>();
    Path remote = temporaryDirectory.resolve("remote");
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.add(exchange.getRequestURI().toString());
          Path file = remote.resolve(exchange.getRequestURI().getPath().substring(1));
          if (Files.isRegularFile(file)) {
            byte[] bytes = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } else {
            exchange.sendResponseHeaders(404, -1);
          }
          exchange.close();
        });
    server.start();
    try {
      Path repository = temporaryDirectory.resolve("cached-repository");
      for (String name : java.util.List.of("root", "child")) {
        Path storage = missingChild && name.equals("child") ? remote : repository;
        Path directory = Files.createDirectories(storage.resolve("test/" + name + "/1"));
        createJar(directory.resolve(name + "-1.jar"), "test/Value.class", name);
        String dependencies =
            name.equals("root")
                ? """
            <dependencies><dependency><groupId>test</groupId><artifactId>child</artifactId><version>1</version></dependency></dependencies>
            <repositories><repository><id>inherited</id><url>http://127.0.0.1:%d/</url></repository></repositories>
            """
                    .formatted(server.getAddress().getPort())
                : "";
        Files.writeString(
            directory.resolve(name + "-1.pom"),
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>%s</artifactId><version>1</version>%s
            </project>
            """
                .formatted(name, dependencies));
      }
      for (int attempt = 0; attempt < 2; attempt++) {
        try (var resolver = new JarResolver(repository, progress::add)) {
          var graph =
              resolver.resolve(
                  temporaryDirectory,
                  new JarBinding(
                      new MavenJarTarget(
                          new MavenArtifactCoordinate("test", "root", "1"), Optional.empty())));
          assertEquals(2, graph.artifacts().size());
        }
        assertTrue(requests.stream().noneMatch(path -> path.contains("prefixes")));
        if (missingChild && attempt == 0) {
          assertTrue(requests.contains("/test/child/1/child-1.jar"));
          assertTrue(
              progress.stream()
                  .anyMatch(
                      message ->
                          message.startsWith("Downloading ") && message.endsWith("child-1.jar")));
          assertTrue(
              progress.stream()
                  .anyMatch(message -> message.startsWith("Downloaded test/child/1/child-1.jar")));
        } else {
          assertEquals(java.util.List.of(), requests);
          assertEquals(java.util.List.of(), progress);
        }
        requests.clear();
        progress.clear();
      }
    } finally {
      server.stop(0);
    }
  }

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
            "lib/sample.jar", Optional.of(Sha256Digest.parse("0123456789abcdef".repeat(4))));

    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("cache"))) {
      IOException exception =
          assertThrows(
              IOException.class, () -> resolver.resolve(moduleRoot, new JarBinding(target)));

      assertTrue(exception.getMessage().contains("integrity"));
    }
  }

  @Test
  void resolvesApacheCommonsLangCoordinates() throws Exception {
    var coordinate = new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0");

    try (JarResolver resolver =
        new JarResolver(MavenTestRepository.prepare(temporaryDirectory.resolve("maven-cache")))) {
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

  @Test
  void resolvesJavaArtifactsFromItsOwnRepository() throws Exception {
    Path jarCache = temporaryDirectory.resolve("jar-cache");
    Path javaArtifact = Files.createDirectories(jarCache.resolve("sample/java-library/1"));
    createJar(javaArtifact.resolve("java-library-1.jar"), "sample/Value.class", "value");
    Files.writeString(
        javaArtifact.resolve("java-library-1.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>sample</groupId>
          <artifactId>java-library</artifactId>
          <version>1</version>
        </project>
        """);

    try (JarResolver resolver = new JarResolver(jarCache)) {
      ResolvedJarGraph graph =
          resolver.resolve(
              temporaryDirectory,
              new JarBinding(
                  new MavenJarTarget(
                      new MavenArtifactCoordinate("sample", "java-library", "1"),
                      Optional.empty())));

      assertEquals(
          javaArtifact.resolve("java-library-1.jar").toAbsolutePath().normalize(),
          graph.root().file());
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
