package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StructuredCommandsTest {
  @TempDir Path directory;

  @Test
  void checksDeclarationsWithoutRunningAnEntryPoint() throws IOException {
    Path source = directory.resolve("library.norm");
    Files.writeString(
        source,
        "Integer answer() { return 42 } Void main() { require(condition: false, message: \"must not run\") }");
    Result result = run("check", source.toString(), "--format", "json");
    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.error());
    assertEquals("success", result.json().get("status").getAsString());
    assertEquals(1, result.json().get("schemaVersion").getAsInt());
    assertTrue(result.json().getAsJsonArray("diagnostics").isEmpty());
    Files.writeString(source, "Integer answer() { return 42 }");
    assertEquals(ExitCode.SUCCESS, run("check", source.toString()).exitCode());
  }

  @Test
  void returnsCompilerFactsAndUtf16Offsets() throws IOException {
    Path source = directory.resolve("invalid.norm");
    String code = "String emoji() { return \"😀\" }\r\nInteger answer() { return missing }";
    Files.writeString(source, code);
    Result result = run("check", source.toString(), "--format", "json");
    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode(), result.output() + result.error());
    assertEquals("compilation_error", result.json().get("status").getAsString());
    JsonObject diagnostic = result.json().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
    assertEquals("NORM-NAME-0003", diagnostic.get("code").getAsString());
    JsonObject location = diagnostic.getAsJsonObject("location");
    assertEquals(source.toUri().toString(), location.get("uri").getAsString());
    assertEquals(code.indexOf("missing"), location.get("startOffset").getAsInt());
    assertEquals(code.indexOf("missing") + 7, location.get("endOffset").getAsInt());
    assertTrue(result.error().isBlank(), result.error());
  }

  @Test
  void checksTheWholeModuleIncludingTests() throws IOException {
    Path module = Files.createDirectories(directory.resolve("checked"));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"checked\", version: 1) }");
    Files.writeString(module.resolve("api.norm"), "package checked Integer answer() { return 42 }");
    Files.createDirectories(module.resolve("tests"));
    Files.writeString(
        module.resolve("tests/case.norm"),
        "package checked import std.testing.Test @Test Void broken() { missing() }");
    Result result = run("check", module.toString(), "--format", "json");
    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode(), result.output() + result.error());
    assertTrue(result.output().contains("missing"));
  }

  @Test
  void preservesModuleConfigurationDiagnostics() throws IOException {
    Path module = Files.createDirectories(directory.resolve("invalid"));
    Files.writeString(module.resolve("module.norm"), "Module module() { return missing }");
    Files.writeString(module.resolve("api.norm"), "Integer answer() { return 42 }");
    Result result = run("check", module.toString(), "--format", "json");
    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode(), result.output());
    assertFalse(result.json().getAsJsonArray("diagnostics").isEmpty());
    assertTrue(
        result
            .json()
            .getAsJsonArray("diagnostics")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("location")
            .get("uri")
            .getAsString()
            .endsWith("module.norm"));
  }

  @Test
  void reportsInputAndUsageErrorsAsJson() {
    Result missing = run("check", directory.resolve("absent.norm").toString(), "--format", "json");
    assertEquals(ExitCode.INPUT_ERROR, missing.exitCode());
    assertEquals("input_error", missing.json().get("status").getAsString());
    Result invalid = run("test", "--format", "json");
    assertEquals(ExitCode.USAGE_ERROR, invalid.exitCode());
    assertEquals("usage_error", invalid.json().get("status").getAsString());
    assertEquals(ExitCode.USAGE_ERROR, run("check", "source.norm", "--filter", "x").exitCode());
  }

  @Test
  void separatesTestLogsAndPreservesFiltering() throws IOException {
    Path source = directory.resolve("tests.norm");
    Files.writeString(
        source,
        "import std.testing.Test @Test Void passes() { printLine(\"agent-log\") } @Test Void fails() { require(condition: false, message: \"broken\") }");
    Result result = run("test", source.toString(), "--format", "json", "--filter", "passes");
    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.output() + result.error());
    assertEquals(1, result.json().getAsJsonObject("tests").get("passed").getAsInt());
    assertTrue(result.error().contains("agent-log"), result.error());
    assertFalse(result.output().contains("agent-log"));
    Result failed = run("test", source.toString(), "--filter", "fails", "--format", "json");
    assertEquals(ExitCode.TEST_FAILURE, failed.exitCode(), failed.error());
    assertEquals("test_failure", failed.json().get("status").getAsString());
    assertEquals(1, failed.json().getAsJsonObject("tests").get("failed").getAsInt());
    assertTrue(failed.output().contains("broken"));
    Result empty = run("test", source.toString(), "--filter", "absent", "--format", "json");
    assertEquals(ExitCode.TEST_FAILURE, empty.exitCode());
    assertEquals("no_tests", empty.json().get("status").getAsString());
  }

  private Result run(String... arguments) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    int code = new CliController().run(arguments, new PrintWriter(out), new PrintWriter(err));
    return new Result(code, out.toString(), err.toString());
  }

  private record Result(int exitCode, String output, String error) {
    JsonObject json() {
      return JsonParser.parseString(output).getAsJsonObject();
    }
  }
}
