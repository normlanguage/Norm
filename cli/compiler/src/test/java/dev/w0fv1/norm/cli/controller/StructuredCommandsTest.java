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
  void checksDirectoryWithEmbeddedModuleInMain() throws IOException {
    Path project = Files.createDirectories(directory.resolve("single-file"));
    Files.writeString(
        project.resolve("main.norm"),
        "Module module() { module(name: \"single_file\", version: 1) } "
            + "package single_file import std.testing.Test @Test Void passes() {} Void main() {} ");
    Result result = run("check", project.toString(), "--format", "json");
    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.output() + result.error());
    assertEquals("success", result.json().get("status").getAsString());
    Result tested = run("test", project.toString(), "--format", "json");
    assertEquals(ExitCode.SUCCESS, tested.exitCode(), tested.output() + tested.error());
    assertEquals(1, tested.json().getAsJsonObject("tests").get("passed").getAsInt());
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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"main.norm", "application.norm"})
  void moduleCommandsUseEmbeddedConfigurationWithoutLosingProgramSource(String file)
      throws IOException {
    Path project = Files.createDirectories(directory.resolve("embedded"));
    Path entry = project.resolve(file);
    String source =
        "package embedded Integer answer() { return 42 } "
            + "Module module() { module(name: \"embedded\", version: 1) }";
    Files.writeString(entry, source);
    Result updated = run("update", "--project", project.toString());
    assertEquals(ExitCode.SUCCESS, updated.exitCode(), updated.error());
    assertEquals(source, Files.readString(entry));
    Result docs = run("docs", project.toString(), "--output", directory.resolve("api").toString());
    assertEquals(ExitCode.SUCCESS, docs.exitCode(), docs.error());
    Result packaged =
        run("package", project.toString(), "--output", directory.resolve("repository").toString());
    assertEquals(ExitCode.SUCCESS, packaged.exitCode(), packaged.error());
    try (var files = Files.walk(directory.resolve("repository"))) {
      Path artifact =
          files.filter(path -> path.toString().endsWith(".nar")).findFirst().orElseThrow();
      try (var archive = new java.util.zip.ZipFile(artifact.toFile())) {
        var program = archive.getEntry("sources/embedded/" + file);
        assertNotNull(program);
        String text =
            new String(
                archive.getInputStream(program).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("Integer answer() { return 42 }"));
        assertFalse(text.contains("Module module()"));
      }
    }
    Result resolved = run("resolve", project.toString());
    assertTrue(
        resolved.error().contains("module does not declare a JAR binding"), resolved.error());
  }

  @Test
  void resolvesBindingInEmbeddedConfigurationAndPreservesBusinessCode() throws IOException {
    Path project = Files.createDirectories(directory.resolve("binding"));
    Path entry = project.resolve("main.norm");
    String program = "Void main() { printLine(\"preserved\") }\n";
    Files.writeString(
        entry,
        program
            + "Module module() { module(name: \"binding\", version: 1, binding: jarBinding(target: jdkModule(\"java.base\"), api: [])) }");
    Result result = run("resolve", project.toString());
    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.error());
    String updated = Files.readString(entry);
    assertTrue(updated.startsWith(program));
    assertTrue(updated.contains("resolution: sha256("));
    Result repeated = run("resolve", project.toString());
    assertEquals(ExitCode.SUCCESS, repeated.exitCode(), repeated.error());
    assertEquals(updated, Files.readString(entry));
    Result packaged =
        run(
            "package",
            project.toString(),
            "--output",
            directory.resolve("binding-repo").toString());
    assertEquals(ExitCode.SUCCESS, packaged.exitCode(), packaged.error());
    Result checked = run("check", project.toString());
    assertEquals(ExitCode.SUCCESS, checked.exitCode(), checked.error());
  }

  @Test
  void embeddedModuleOwnsItsDirectoryInsideAnotherModule() throws IOException {
    Files.writeString(
        directory.resolve("module.norm"),
        "Module module() { module(name: \"parent\", version: 1) }");
    Files.writeString(
        directory.resolve("api.norm"), "package parent Integer answer() { return 1 }");
    Path child = Files.createDirectories(directory.resolve("child"));
    Files.writeString(
        child.resolve("main.norm"),
        "package child Integer answer() { return 2 } Module module() { module(name: \"child\", version: 1) }");
    for (Path root : java.util.List.of(directory, child)) {
      Result checked = run("check", root.toString());
      assertEquals(ExitCode.SUCCESS, checked.exitCode(), checked.error());
      Result docs =
          run(
              "docs",
              root.toString(),
              "--output",
              directory.resolveSibling(root.getFileName() + "-api").toString());
      assertEquals(ExitCode.SUCCESS, docs.exitCode(), docs.error());
      Result packaged =
          run(
              "package",
              root.toString(),
              "--output",
              directory.resolveSibling(root.getFileName() + "-repo").toString());
      assertEquals(ExitCode.SUCCESS, packaged.exitCode(), packaged.error());
    }
  }

  @Test
  void resolvesStandaloneBindingDeclaredByHelper() throws IOException {
    Path module = directory.resolve("module.norm");
    Files.writeString(
        module,
        "JarTarget target() { return jdkModule(\"java.base\") } Module module() { module(name: \"helper\", version: 1, binding: jarBinding(target: target(), api: [])) }");
    Result resolved = run("resolve", directory.toString());
    assertEquals(ExitCode.SUCCESS, resolved.exitCode(), resolved.error());
    assertTrue(Files.readString(module).contains("resolution: sha256("));
  }

  @Test
  void reportsMissingModuleDeclarationWithoutCrashing() throws IOException {
    Files.writeString(directory.resolve("main.norm"), "Void main() {}");
    Result result =
        run("docs", directory.toString(), "--output", directory.resolve("api").toString());
    assertEquals(ExitCode.INPUT_ERROR, result.exitCode(), result.error());
    assertTrue(result.error().contains("does not contain a module declaration"));
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
