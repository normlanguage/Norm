package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

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

final class PreparedApplicationTest {
  @TempDir Path directory;

  @Test
  void executesAfterCompilerClosesAndSourceDisappears() throws Exception {
    Path entry =
        Files.writeString(
            directory.resolve("main.norm"), "Void main() { printLine(\"prepared\") }");
    Path output = directory.resolve("prepared");
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compilation = runner.compileApplication(entry)) {
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      new PreparedApplicationWriter().write(compilation.application().orElseThrow(), output);
    }
    Files.delete(entry);
    for (int index = 0; index < 2; index++) {
      var text = new StringWriter();
      PreparedApplication.read(output).execute(output, ExecutionContext.of(new PrintWriter(text)));
      assertEquals("prepared" + System.lineSeparator(), text.toString());
    }
    try (var files = Files.walk(output)) {
      assertFalse(files.anyMatch(path -> path.toString().endsWith(".norm")));
    }
  }
}
