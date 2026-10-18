package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RunCommandTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"main.norm", "application.norm"})
  void runsCurrentModuleAndForwardsArguments(String file) throws Exception {
    Path module = Files.createDirectories(directory.resolve("Norm-agent"));
    Files.writeString(module.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Files.writeString(module.resolve(file),
        "package sample import std.application.arguments Void main() { printLine(arguments()[0]) }");
    var output = new StringWriter();
    var error = new StringWriter();
    int result = new RunCommand(module).execute(List.of("--debug", "--", "hello"),
        new PrintWriter(output), new PrintWriter(error));
    assertEquals(ExitCode.SUCCESS, result, error.toString());
    assertEquals("hello" + System.lineSeparator(), output.toString());
    assertTrue(error.toString().contains("Starting application"), error.toString());
  }

  @Test
  void bareRunAndExplicitDirectorySelectTheSameEntry() throws Exception {
    Path module = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(module.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Files.writeString(module.resolve("application.norm"),
        "package sample Void main() { printLine(\"selected\") }");
    for (List<String> arguments : List.of(List.<String>of(), List.of("."))) {
      var output = new StringWriter();
      var error = new StringWriter();
      int result = new RunCommand(module).execute(arguments,
          new PrintWriter(output), new PrintWriter(error));
      assertEquals(ExitCode.SUCCESS, result, error.toString());
      assertEquals("selected" + System.lineSeparator(), output.toString());
      assertEquals("", error.toString());
    }
  }
  @Test
  void reportsMissingDefaultEntryInsteadOfUsageError() {
    var error = new StringWriter();
    int result = new RunCommand(directory).execute(List.of(),
        new PrintWriter(new StringWriter()), new PrintWriter(error));
    assertEquals(ExitCode.INPUT_ERROR, result);
    assertTrue(error.toString().contains("NORM-CLI-0004"), error.toString());
  }

  @Test
  void rejectsMultiplePaths() {
    var error = new StringWriter();
    int result = new RunCommand(directory).execute(List.of("one.norm", "two.norm"),
        new PrintWriter(new StringWriter()), new PrintWriter(error));
    assertEquals(ExitCode.USAGE_ERROR, result);
  }
}
