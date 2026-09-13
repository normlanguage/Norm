package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.runtime.PreparedApplication;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApplicationBundleWriterTest {
  @Test
  void bundlesCompiledDependenciesAndCapturedResources(@TempDir Path root) throws Exception {
    Path app = Files.createDirectories(root.resolve("app"));
    Path library = Files.createDirectories(root.resolve("dependencies/example/library"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "app", version: 1, dependencies: [
          dependency(repository: "github", name: "example.library", version: 1)
        ]) }
        """);
    Path entry =
        Files.writeString(
            app.resolve("main.norm"),
            """
        package app
        import example.library.value
        Void main() { printLine(value()) }
        """);
    Files.writeString(
        library.resolve("module.norm"),
        """
        Module module() { module(name: "example.library", version: 1, exports: ["value"]) }
        """);
    Path source =
        Files.writeString(
            library.resolve("value.norm"),
            "package example.library public String value() { \"captured\" }");
    Path resources = Files.createDirectories(library.resolve("resources"));
    Path resource = Files.writeString(resources.resolve("value.txt"), "captured resource");
    Path bundle = root.resolve("delivery");
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compiled = runner.compileApplication(entry)) {
      assertTrue(compiled.result().isSuccess(), compiled.result().diagnostics().toString());
      Files.writeString(source, "invalid source");
      Files.writeString(resource, "changed resource");
      Files.writeString(resources.resolve("later.txt"), "new resource");
      new ApplicationBundleWriter().writeDirectory(compiled.application().orElseThrow(), bundle);
    }
    Files.delete(entry);
    var output = new StringWriter();
    PreparedApplication.read(bundle).execute(bundle, ExecutionContext.of(new PrintWriter(output)));
    assertEquals("captured" + System.lineSeparator(), output.toString());
    assertEquals("captured resource", Files.readString(bundle.resolve("classes/value.txt")));
    assertFalse(Files.exists(bundle.resolve("classes/later.txt")));
    assertFalse(Files.exists(bundle.resolve("source")));
    assertFalse(Files.exists(bundle.resolve("packages")));
  }
}
