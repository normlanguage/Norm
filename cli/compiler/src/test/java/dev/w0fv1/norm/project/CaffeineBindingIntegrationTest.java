package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CaffeineBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheCaffeineNarForBoundedExpiryLoadingInvalidationAndStats() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/caffeine"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/caffeine/caffeine/cache/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    install(
        workspace,
        repository,
        "com/github/ben-manes/caffeine/caffeine/3.2.4",
        "caffeine-3.2.4.jar");
    install(workspace, repository, "org/jspecify/jspecify/1.0.0", "jspecify-1.0.0.jar");
    install(
        workspace,
        repository,
        "com/google/errorprone/error_prone_annotations/2.49.0",
        "error_prone_annotations-2.49.0.jar");
    Files.writeString(
        repository.resolve("com/github/ben-manes/caffeine/caffeine/3.2.4/caffeine-3.2.4.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.github.ben-manes.caffeine</groupId>
          <artifactId>caffeine</artifactId>
          <version>3.2.4</version>
          <dependencies>
            <dependency>
              <groupId>org.jspecify</groupId>
              <artifactId>jspecify</artifactId>
              <version>1.0.0</version>
            </dependency>
            <dependency>
              <groupId>com.google.errorprone</groupId>
              <artifactId>error_prone_annotations</artifactId>
              <version>2.49.0</version>
            </dependency>
          </dependencies>
        </project>
        """);
    Files.writeString(
        repository.resolve("org/jspecify/jspecify/1.0.0/jspecify-1.0.0.pom"),
        minimalPom("org.jspecify", "jspecify", "1.0.0"));
    Files.writeString(
        repository.resolve(
            "com/google/errorprone/error_prone_annotations/2.49.0/error_prone_annotations-2.49.0.pom"),
        minimalPom("com.google.errorprone", "error_prone_annotations", "2.49.0"));

    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "app",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "caffeine.cache", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import caffeine.cache.Cache
        import caffeine.cache.CacheStats
        import caffeine.cache.Caffeine
        import caffeine.cache.caffeineNewBuilder
        import std.time.Duration
        import std.time.duration

        Void main() {
          Caffeine<Any?, Any?>? builder = caffeineNewBuilder()
          if builder != null {
            Caffeine<Any?, Any?>? bounded = builder.maximumSize(2)
            if bounded != null {
              Duration ttl = duration(seconds: 60, nanoseconds: 0)
              Caffeine<Any?, Any?>? expiring = bounded.expireAfterWrite(ttl)
              if expiring != null {
                Caffeine<Any?, Any?>? measured = expiring.recordStats()
                if measured != null {
                  Cache<String?, String?>? cache = measured.build<String, String>()
                  if cache != null {
                    cache.put(arg0: "a", arg1: "one")
                    printLine(cache.getIfPresent("a") ?? "missing")
                    Function<String?(String?)> loader = (key) {
                      return (key ?? "") + "-loaded"
                    }
                    printLine(cache.get(arg0: "b", arg1: loader) ?? "missing")
                    printLine(cache.getIfPresent("b") ?? "missing")
                    cache.invalidate("a")
                    cache.cleanUp()
                    printLine(cache.estimatedSize())
                    CacheStats? stats = cache.stats()
                    if stats != null {
                      printLine(stats.hitCount())
                      printLine(stats.missCount())
                      printLine(stats.loadSuccessCount())
                    }
                  }
                }
              }
            }
          }
        }
        """);
    StringWriter output = new StringWriter();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        String.join(System.lineSeparator(), "one", "b-loaded", "b-loaded", "1", "2", "1", "1", ""),
        output.toString());
  }

  private static void install(Path workspace, Path repository, String coordinate, String fileName)
      throws Exception {
    Path directory = Files.createDirectories(repository.resolve(coordinate));
    Files.copy(
        workspace.resolve("java-binding/caffeine/caffeine/cache/lib").resolve(fileName),
        directory.resolve(fileName));
  }

  private static String minimalPom(String group, String artifact, String version) {
    return """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
        </project>
        """
        .formatted(group, artifact, version);
  }
}
