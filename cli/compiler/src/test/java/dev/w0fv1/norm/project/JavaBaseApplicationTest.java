package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.application.PreparedApplicationWriter;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaBaseApplicationTest {
  @TempDir Path directory;

  @Test
  void packagedCompilerStartsAsANamedJavaModule() throws Exception {
    Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
    String modulePath =
        System.getProperty("norm.test.compilerJar")
            + java.io.File.pathSeparator
            + Path.of(System.getProperty("norm.test.runtimeDirectory"), "lib");
    Path output = directory.resolve("startup.txt");
    Process process =
        new ProcessBuilder(
                javaExecutable.toString(),
                "--module-path",
                modulePath,
                "-m",
                "dev.w0fv1.norm/dev.w0fv1.norm.cli.Main",
                "--version")
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertTrue(
          process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "CLI startup timed out");
      assertEquals(0, process.exitValue(), Files.readString(output));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  @Test
  void standaloneProgramsUseTheSingleJavaBaseBindingAndRetainItsCalls() throws Exception {
    Path source = directory.resolve("Main.norm");
    Files.writeString(
        source,
        """
        import java.base.util.ArrayList
        import java.base.util.arrayListNew

        Void main() {
          ArrayList<String> values = arrayListNew<String>()
          values.add("owned")
          printLine(values.get(0))
          printLine(values.size())
        }
        """);
    var backend = new NormRuntime();
    var environment = ProjectEnvironment.bootstrap(backend);
    var output = new StringWriter();
    try (var runner = ApplicationRunner.open(environment)) {
      var result = runner.run(source, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      try (var compilation = runner.compileApplication(source)) {
        assertTrue(compilation.result().isSuccess());
        var content =
            new PreparedApplicationWriter().capture(compilation.application().orElseThrow());
        assertTrue(
            content.program().bindings().stream().anyMatch(binding -> !binding.calls().isEmpty()));
        assertTrue(content.dependencies().isEmpty());
      }
    }
    assertEquals(
        "owned" + System.lineSeparator() + "1" + System.lineSeparator(), output.toString());
  }
}
