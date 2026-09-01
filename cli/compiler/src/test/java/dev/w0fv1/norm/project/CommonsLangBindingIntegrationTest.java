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

final class CommonsLangBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheCommonsLangNarThroughThePublishedNormExample() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null
        && !Files.isDirectory(workspace.resolve("java-binding/commons-lang"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/commons-lang/commons/lang/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path upstream =
        Files.createDirectories(repository.resolve("org/apache/commons/commons-lang3/3.20.0"));
    Files.copy(
        workspace.resolve("java-binding/commons-lang/commons/lang/lib/commons-lang3-3.20.0.jar"),
        upstream.resolve("commons-lang3-3.20.0.jar"));
    Files.writeString(
        upstream.resolve("commons-lang3-3.20.0.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.apache.commons</groupId>
          <artifactId>commons-lang3</artifactId>
          <version>3.20.0</version>
        </project>
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("application/object/app"));
    Path example = workspace.resolve("docs/examples/java-commons-lang/object/app");
    Files.copy(example.resolve("module.norm"), app.resolve("module.norm"));
    Path entry = app.resolve("Main.norm");
    Files.copy(example.resolve("Main.norm"), entry);

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
        String.join(System.lineSeparator(), "2", "Norm", "NAR", "first", "second", ""),
        output.toString());
  }
}
