package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QueryCommandTest {
  @Test
  void exposesDefaultParametersAndGenericContracts() throws Exception {
    Path source = directory.resolve("contracts.norm");
    Files.writeString(
        source,
        "Integer amount(Integer value = 1) { return value } T identity<T>(T value) { return value }");
    var defaults =
        run("query", source.toString(), "--search", "amount")
            .getAsJsonObject("query")
            .getAsJsonObject("symbols")
            .getAsJsonArray("items")
            .get(0)
            .getAsJsonObject();
    assertTrue(
        defaults
            .getAsJsonArray("parameters")
            .get(0)
            .getAsJsonObject()
            .get("hasDefault")
            .getAsBoolean());
    assertEquals(
        "Integer",
        defaults
            .getAsJsonArray("parameters")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("type")
            .get("displayName")
            .getAsString());
    var generic =
        run("query", source.toString(), "--search", "identity")
            .getAsJsonObject("query")
            .getAsJsonObject("symbols")
            .getAsJsonArray("items")
            .get(0)
            .getAsJsonObject();
    assertEquals(
        "T",
        generic
            .getAsJsonArray("typeParameters")
            .get(0)
            .getAsJsonObject()
            .get("name")
            .getAsString());
    assertEquals(
        generic.getAsJsonObject("type").get("identity"),
        generic
            .getAsJsonArray("parameters")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("type")
            .get("identity"));
  }

  @TempDir Path directory;

  @Test
  void searchesThenResolvesCurrentQualifiedDeclaration() throws Exception {
    Path source = directory.resolve("query.norm");
    Files.writeString(source, "Integer answer() { return 42 } Void main() { printLine(answer()) }");
    var result = run("query", source.toString(), "--search", "answer");
    assertEquals(0, result.get("exitCode").getAsInt(), result.toString());
    assertFalse(result.getAsJsonObject("query").has("documents"));
    var declaration =
        result
            .getAsJsonObject("query")
            .getAsJsonObject("symbols")
            .getAsJsonArray("items")
            .get(0)
            .getAsJsonObject();
    var context = run("query", source.toString(), "answer", "--source");
    assertEquals(
        "Integer answer() { return 42 }",
        context
            .getAsJsonObject("query")
            .getAsJsonObject("context")
            .getAsJsonObject("source")
            .get("text")
            .getAsString());
    Files.writeString(source, "Integer answer() { return 43 }");
    var current = run("query", source.toString(), "answer", "--source");
    assertTrue(current.toString().contains("return 43"));
  }

  @Test
  void describesModuleOwnershipAndAssociatedTests() throws Exception {
    Path module = Files.createDirectories(directory.resolve("queried"));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"queried\", version: 1) }");
    Files.writeString(module.resolve("api.norm"), "package queried Integer answer() { return 42 }");
    Files.createDirectories(module.resolve("tests"));
    Files.writeString(
        module.resolve("tests/case.norm"),
        "package queried import std.testing.Test @Test(functions: [answer.function]) Void verifies() { require(condition: answer() == 42, message: \"answer\") }");
    var result = run("query", module.toString());
    var data = result.getAsJsonObject("query");
    assertEquals(
        "queried",
        data.getAsJsonArray("modules").get(0).getAsJsonObject().get("name").getAsString());
    assertTrue(
        data.getAsJsonArray("documents").asList().stream()
            .anyMatch(item -> item.getAsJsonObject().get("testSource").getAsBoolean()));
    var context = run("query", module.toString(), "queried.answer", "--tests");
    assertEquals(
        1,
        context
            .getAsJsonObject("query")
            .getAsJsonObject("context")
            .getAsJsonObject("tests")
            .get("total")
            .getAsInt());
    var renamed = run("refactor", "name", module.toString(), "queried.answer", "--to", "result");
    assertEquals(0, renamed.get("exitCode").getAsInt(), renamed.toString());
    assertFalse(renamed.toString().contains("\"oldText\":\"function\""));
  }

  @Test
  void previewsRenameWithoutWritingAndReturnsBothDiagnosticSets() throws Exception {
    Path source = directory.resolve("rename.norm");
    String original = "Integer answer() { return 42 } Void main() { printLine(answer()) }";
    Files.writeString(source, original);
    var preview =
        run("refactor", "name", source.toString(), "answer", "--to", "result", "--preview");
    assertEquals(0, preview.get("exitCode").getAsInt(), preview.toString());
    assertEquals(original, Files.readString(source));
    var edits =
        preview
            .getAsJsonObject("refactor")
            .getAsJsonArray("changes")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("edits");
    assertEquals(2, edits.size());
    StringBuilder changed = new StringBuilder(original);
    for (var item : edits) {
      var edit = item.getAsJsonObject();
      changed.replace(
          edit.get("startOffset").getAsInt(),
          edit.get("endOffset").getAsInt(),
          edit.get("newText").getAsString());
    }
    Files.writeString(source, changed);
    assertEquals(0, run("check", source.toString(), "--format", "json").get("exitCode").getAsInt());
    assertTrue(preview.getAsJsonObject("refactor").getAsJsonArray("beforeDiagnostics").isEmpty());
    assertTrue(preview.getAsJsonArray("diagnostics").isEmpty());
  }

  @Test
  void returnsDiagnosticsAlongsideQueryResultsAndRejectsIncompleteSelectors() throws Exception {
    Path source = directory.resolve("broken.norm");
    Files.writeString(source, "Integer answer() { return missing }");
    var result = run("query", source.toString(), "--search", "answer", "--limit", "1");
    assertEquals("compilation_error", result.get("status").getAsString());
    assertEquals(
        1, result.getAsJsonObject("query").getAsJsonObject("symbols").get("total").getAsInt());
    assertEquals(
        "usage_error",
        run("query", source.toString(), "--symbol", "anything").get("status").getAsString());
  }

  private JsonObject run(String... arguments) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    int code = new CliController().run(arguments, new PrintWriter(out), new PrintWriter(err));
    JsonObject json = JsonParser.parseString(out.toString()).getAsJsonObject();
    assertEquals(code, json.get("exitCode").getAsInt());
    assertEquals("", err.toString());
    return json;
  }
}
