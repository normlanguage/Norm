package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CompilationWorkspaceTest {
  @TempDir Path directory;

  @Test
  void sourceExecutionUsesSourceDirectoryRatherThanCompilerOrWorkingDirectory() throws Exception {
    Path source =
        Files.writeString(
            directory.resolve("location.norm"),
            "import std.application.applicationDirectory Void main() { printLine(applicationDirectory().value) }");
    var output = new java.io.StringWriter();
    try (var launcher = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      var result = launcher.run(source, ExecutionContext.of(new java.io.PrintWriter(output)));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    assertEquals(directory.toString() + System.lineSeparator(), output.toString());
  }

  @Test
  void ownsGeneratedResourcesIndependentlyOfCompilationSession() throws Exception {
    Path source = Files.writeString(directory.resolve("web.norm"), "Void main() {}");
    Path classes;
    ApplicationCompilation compilation;
    try (var launcher = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      compilation = launcher.compileApplication(source);
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      classes = compilation.application().orElseThrow().annotations().classes();
      assertFalse(classes.startsWith(directory));
      Files.createDirectories(classes);
      Files.writeString(classes.resolve("resource.txt"), "application resource");
      try (var files = Files.list(directory)) {
        assertEquals(java.util.List.of(source), files.toList());
      }
    }
    assertTrue(Files.exists(classes));
    compilation.close();
    assertFalse(Files.exists(classes));
  }
}
