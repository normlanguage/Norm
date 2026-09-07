package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

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
    try (var launcher = ProjectEnvironment.bootstrap(new NormRuntime()).launcher()) {
      var result =
          launcher.run(
              source,
              dev.w0fv1.norm.execution.ExecutionContext.of(new java.io.PrintWriter(output)));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    assertEquals(directory.toString() + System.lineSeparator(), output.toString());
  }

  @Test
  void ownsGeneratedResourcesUntilCompilationSessionCloses() throws Exception {
    Path source = Files.writeString(directory.resolve("web.norm"), "Void main() {}");
    Path classes;
    try (var launcher = ProjectEnvironment.bootstrap(new NormRuntime()).launcher()) {
      var compilation = launcher.compileApplication(source);
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      classes = compilation.annotationOutput().orElseThrow().classes();
      assertFalse(classes.startsWith(directory));
      Files.createDirectories(classes);
      Files.writeString(classes.resolve("resource.txt"), "application resource");
      try (var files = Files.list(directory)) {
        assertEquals(java.util.List.of(source), files.toList());
      }
    }
    assertFalse(Files.exists(classes));
  }
}
