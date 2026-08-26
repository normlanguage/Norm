package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliControllerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void printsTheBuildVersion() {
    Result result = run("--version");

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertFalse(BuildMetadata.VERSION.contains("${"));
    assertEquals("norm " + BuildMetadata.VERSION + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void showsHelpForNoArgumentsAndAliases() {
    Result noArguments = run();
    Result alias = run("-h");

    assertEquals(ExitCode.SUCCESS, noArguments.exitCode());
    assertEquals(noArguments.standardOut(), alias.standardOut());
    assertTrue(noArguments.standardOut().contains("Usage: norm <command> [options]"));
    assertTrue(noArguments.standardOut().contains("version"));
  }

  @Test
  void reportsUnknownCommandsOnStandardError() {
    Result result = run("compile");

    assertEquals(ExitCode.USAGE_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-CLI-0001"));
    assertTrue(result.standardError().contains("unknown command 'compile'"));
  }

  @Test
  void rejectsUnexpectedCommandArguments() {
    Result result = run("version", "extra");

    assertEquals(ExitCode.USAGE_ERROR, result.exitCode());
    assertTrue(result.standardError().contains("NORM-CLI-0002"));
  }

  @Test
  void runsAHelloWorldSourceFile() throws IOException {
    Path source = temporaryDirectory.resolve("hello.norm");
    Files.writeString(source, "Void main() { printLine(\"Hello from Norm\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("Hello from Norm" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void loadsAndRunsTheEntrySourceRoot() throws IOException {
    Path app = Files.createDirectories(temporaryDirectory.resolve("src/sample"));
    Path math = Files.createDirectories(temporaryDirectory.resolve("src/sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry, "package sample import sample.math.twice Void main() { printLine(twice(7)) }");
    Files.writeString(
        math.resolve("Numbers.norm"),
        "package sample.math public Integer twice(Integer value) { return value * 2 }");
    Files.writeString(
        temporaryDirectory.resolve("src/sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"math.Numbers\"]) }");

    Result result = run("run", entry.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("14" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void runsPackagedStandardLibrarySources() throws IOException {
    Path source = temporaryDirectory.resolve("math.norm");
    Files.writeString(
        source,
        "import std.math.clamp Void main() { "
            + "printLine(clamp(value: 12, minimum: 0, maximum: 9)) }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("9" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void rendersCompilerDiagnosticsForInvalidPrograms() throws IOException {
    Path source = temporaryDirectory.resolve("invalid.norm");
    Files.writeString(source, "Void main() { missing(\"value\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-NAME-0003"));
    assertTrue(result.standardError().contains("cannot find function or type 'missing'"));
  }

  @Test
  void rendersGuestRuntimeErrors() throws IOException {
    Path source = temporaryDirectory.resolve("runtime-error.norm");
    Files.writeString(source, "Void main() {\n  printLine(1 / 0)\n}");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.RUNTIME_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-RUNTIME-0004"));
    assertTrue(result.standardError().contains(source.toUri().toString()));
    assertTrue(result.standardError().contains(":2:"));
  }

  private static Result run(String... arguments) {
    var standardOut = new StringWriter();
    var standardError = new StringWriter();
    int exitCode =
        new CliController()
            .run(arguments, new PrintWriter(standardOut), new PrintWriter(standardError));
    return new Result(exitCode, standardOut.toString(), standardError.toString());
  }

  private record Result(int exitCode, String standardOut, String standardError) {}
}
