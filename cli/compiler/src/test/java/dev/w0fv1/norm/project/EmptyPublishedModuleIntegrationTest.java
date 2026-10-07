package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EmptyPublishedModuleIntegrationTest {
  @TempDir Path directory;

  @Test
  void importsAnEmptyPublishedModuleWithImplicitPreludeOwnership() throws Exception {
    var publisher = Files.createDirectories(directory.resolve("empty"));
    var descriptor =
        Files.writeString(
            publisher.resolve("module.norm"),
            "Module module() { module(name: \"empty\", version: 1, exports: []) }");
    var repository = directory.resolve("repository");
    var runtime = new NormRuntime();
    try (var environment = ProjectEnvironment.bootstrap(runtime)) {
      try (var projects = environment.projectLoader(repository);
          var compiler = environment.compilerSession()) {
        new ModulePackager(projects, compiler).packageModule(descriptor, repository);
      }
      var consumer = Files.createDirectories(directory.resolve("consumer"));
      Files.writeString(
          consumer.resolve("module.norm"),
          "Module module() { module(name: \"consumer\", version: 1, dependencies: [dependency(repository: \"github\", name: \"empty\", version: 1)]) }");
      var entry =
          Files.writeString(
              consumer.resolve("main.norm"), "package consumer Void main() { printLine(42) }");
      try (var application =
          new ApplicationRunner(
              environment.projectLoader(repository), environment.compilerSession(), runtime)) {
        var result =
            application.run(entry, ExecutionContext.of(new PrintWriter(new StringWriter())));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
      }
    }
  }
}
