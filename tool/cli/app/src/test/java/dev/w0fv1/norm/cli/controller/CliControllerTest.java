package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.cli.component.VersionProvider;
import dev.w0fv1.norm.cli.value.ExitCode;
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
    assertFalse(VersionProvider.current().contains("${"));
    assertEquals(
        "norm " + VersionProvider.current() + System.lineSeparator(), result.standardOut());
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
    Files.writeString(source, "void main() { print(\"Hello from Norm\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("Hello from Norm" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void rendersCompilerDiagnosticsForInvalidPrograms() throws IOException {
    Path source = temporaryDirectory.resolve("invalid.norm");
    Files.writeString(source, "void main() { missing(\"value\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-NAME-0003"));
    assertTrue(result.standardError().contains("cannot find function or type 'missing'"));
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
