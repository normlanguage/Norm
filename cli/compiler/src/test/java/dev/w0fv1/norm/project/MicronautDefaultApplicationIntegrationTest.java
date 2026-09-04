package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautDefaultApplicationIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(300)
  void compilesAnApplicationWithDefaultConfiguration() throws Exception {
    Path entry = temporaryDirectory.resolve("web.norm");
    Files.writeString(
        entry,
        """
        package hello.web

        import micronaut.web.MicronautApplication
        import std.application.Application

        Module module() {
          return module(
            dependencies: [
              dependency(repository: "github", name: "micronaut.web", version: 3)
            ]
          )
        }

        public Application application() {
          return MicronautApplication()
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(PublishedPackageCache.path()),
            environment.compilerSession(),
            backend)) {
      var result = launcher.compile(entry);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }
}
