package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunProgressTest {
  @TempDir Path directory;

  @Test
  void reportsPreparationOnStderrWithoutChangingApplicationOutput() throws Exception {
    var entry = directory.resolve("main.norm");
    Files.writeString(entry, "Void main() { printLine(\"ready\") }");
    var output = new StringWriter();
    var diagnostics = new StringWriter();
    int result =
        new RunCommand()
            .execute(
                List.of("--debug", entry.toString()),
                new PrintWriter(output),
                new PrintWriter(diagnostics));
    assertEquals(0, result);
    assertEquals("ready" + System.lineSeparator(), output.toString());
    var progress = diagnostics.toString();
    assertTrue(progress.startsWith("[run +0.0s] Checking prepared application"), progress);
    assertTrue(progress.contains("Resolving sources and dependencies"), progress);
    assertTrue(progress.contains("Compiling Norm sources"), progress);
    assertTrue(progress.contains("Starting application"), progress);
    var repeatedOutput = new StringWriter();
    var repeatedProgress = new StringWriter();
    assertEquals(
        0,
        new RunCommand()
            .execute(
                List.of(entry.toString(), "--debug"),
                new PrintWriter(repeatedOutput),
                new PrintWriter(repeatedProgress)));
    assertEquals(output.toString(), repeatedOutput.toString());
    assertTrue(repeatedProgress.toString().contains("Reused prepared application"));
    assertFalse(repeatedProgress.toString().contains("Initializing compiler"));
    assertFalse(repeatedProgress.toString().contains("Compiling Norm sources"));
  }

  @Test
  void doesNotReportAnApplicationStartWhenCompilationFails() throws Exception {
    var entry = directory.resolve("invalid.norm");
    Files.writeString(entry, "Void main() { nonexistent() }");
    var diagnostics = new StringWriter();
    int result =
        new RunCommand()
            .execute(
                List.of("--debug", entry.toString()),
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics));
    assertNotEquals(0, result);
    assertTrue(diagnostics.toString().contains("Compiling Norm sources"));
    assertFalse(diagnostics.toString().contains("Starting application"));
  }

  @Test
  void hidesPreparationByDefaultForColdAndPreparedRuns() throws Exception {
    var entry = directory.resolve("quiet.norm");
    Files.writeString(entry, "Void main() { printLine(\"ready\") }");
    for (int attempt = 0; attempt < 2; attempt++) {
      var output = new StringWriter();
      var diagnostics = new StringWriter();
      assertEquals(
          0,
          new CliController()
              .run(
                  new String[] {"run", entry.toString()},
                  new PrintWriter(output),
                  new PrintWriter(diagnostics)));
      assertEquals("ready" + System.lineSeparator(), output.toString());
      assertEquals("", diagnostics.toString());
    }
  }

  @Test
  void preservesCompilationDiagnosticsWithoutDebug() throws Exception {
    var entry = directory.resolve("invalid.norm");
    Files.writeString(entry, "Void main() { nonexistent() }");
    var diagnostics = new StringWriter();
    assertNotEquals(
        0,
        new CliController()
            .run(
                new String[] {"run", entry.toString()},
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics)));
    assertTrue(diagnostics.toString().contains("error["), diagnostics.toString());
    assertFalse(diagnostics.toString().contains("[run +"));
  }
}
