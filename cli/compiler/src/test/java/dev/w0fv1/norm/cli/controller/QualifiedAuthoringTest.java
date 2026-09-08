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

final class QualifiedAuthoringTest {
  @TempDir Path directory;

  @Test
  void selectsFieldsAndGenericParameters() throws Exception {
    Path file = directory.resolve("types.norm");
    Files.writeString(
        file, "package app value Box { Integer code } T identity<T>(T value) { return value }");
    for (String name : new String[] {"app.Box", "app.Box.code", "app.identity.T"}) {
      var result = run("query", file.toString(), name);
      assertEquals(0, result.get("exitCode").getAsInt(), result.toString());
    }
    var result = run("refactor", "name", file.toString(), "app.Box.code", "--to", "status");
    assertEquals(0, result.get("exitCode").getAsInt(), result.toString());
  }

  @Test
  void disambiguatesRepeatedLocalNamesByReportedLocation() throws Exception {
    Path file = directory.resolve("locals.norm");
    Files.writeString(
        file,
        "package app Void use(Boolean flag) { if flag { Integer result = 1 printLine(result) } else { Integer result = 2 printLine(result) } }");
    var result = run("query", file.toString(), "app.use.result");
    var candidates = result.getAsJsonObject("query").getAsJsonObject("candidates");
    assertEquals(2, candidates.get("total").getAsInt(), result.toString());
    for (var item : candidates.getAsJsonArray("items")) {
      var candidate = item.getAsJsonObject();
      var preview =
          run(
              "refactor",
              "name",
              file.toString(),
              candidate.get("selector").getAsString(),
              "--at",
              candidate.get("at").getAsString(),
              "--to",
              "answer");
      assertEquals(0, preview.get("exitCode").getAsInt(), preview.toString());
      assertEquals(
          2,
          preview
              .getAsJsonObject("refactor")
              .getAsJsonArray("changes")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("edits")
              .size());
    }
  }

  @Test
  void selectsQualifiedDeclarationsAndExpandsOnlyRequestedFacts() throws Exception {
    Path file = directory.resolve("api.norm");
    Files.writeString(
        file,
        "package app Integer amount(Integer value) { return value } Integer use() { return amount(2) }");
    var basic =
        run("query", file.toString(), "app.amount")
            .getAsJsonObject("query")
            .getAsJsonObject("context");
    assertEquals(
        "app.amount", basic.getAsJsonObject("declaration").get("qualifiedName").getAsString());
    assertFalse(basic.has("references"));
    assertFalse(basic.has("dependencies"));
    assertFalse(basic.has("tests"));
    assertFalse(basic.has("source"));
    var expanded =
        run("query", file.toString(), "app.amount", "--source", "--references")
            .getAsJsonObject("query")
            .getAsJsonObject("context");
    assertTrue(expanded.has("source"));
    assertEquals(1, expanded.getAsJsonObject("references").get("total").getAsInt());
    assertFalse(expanded.has("tests"));
    var parameter = run("query", file.toString(), "app.amount.value");
    assertEquals(0, parameter.get("exitCode").getAsInt(), parameter.toString());
  }

  @Test
  void returnsCopyableCandidatesForOverloadsAndSupportsParameterSelection() throws Exception {
    Path file = directory.resolve("overloads.norm");
    Files.writeString(
        file,
        "package app Integer amount(Integer value) { return value } String amount(String value) { return value }");
    var ambiguous = run("query", file.toString(), "app.amount");
    assertEquals("input_error", ambiguous.get("status").getAsString());
    var candidates =
        ambiguous.getAsJsonObject("query").getAsJsonObject("candidates").getAsJsonArray("items");
    assertEquals(2, candidates.size());
    for (var item : candidates) {
      var selector = item.getAsJsonObject().get("selector").getAsString();
      assertEquals(0, run("query", file.toString(), selector).get("exitCode").getAsInt());
      assertEquals(
          0, run("query", file.toString(), selector + ".value").get("exitCode").getAsInt());
    }
    assertEquals(
        0, run("query", file.toString(), "app.amount(Integer)").get("exitCode").getAsInt());
  }

  @Test
  void refactorsParameterWithDefaultAndExplicitPreviewWithoutWriting() throws Exception {
    Path file = directory.resolve("rename.norm");
    String original =
        "package app Integer amount(Integer value) { return value } Integer use() { return amount(value: 2) }";
    Files.writeString(file, original);
    var implicit = run("refactor", "name", file.toString(), "app.amount.value", "--to", "quantity");
    var explicit =
        run(
            "refactor",
            "name",
            file.toString(),
            "app.amount.value",
            "--to",
            "quantity",
            "--preview");
    assertEquals(0, explicit.get("exitCode").getAsInt(), explicit.toString());
    assertEquals(implicit, explicit);
    var preview = explicit.getAsJsonObject("refactor");
    assertEquals("name", preview.get("type").getAsString());
    assertFalse(preview.get("writesFiles").getAsBoolean());
    assertEquals(
        3,
        preview.getAsJsonArray("changes").get(0).getAsJsonObject().getAsJsonArray("edits").size());
    assertEquals(original, Files.readString(file));
  }

  @Test
  void helpNeedsNoProjectAndInvalidOptionsFail() {
    for (String[] args :
        new String[][] {{"-h"}, {"query", "-h"}, {"refactor", "-h"}, {"refactor", "name", "-h"}}) {
      var out = new StringWriter();
      assertEquals(
          0,
          new CliController().run(args, new PrintWriter(out), new PrintWriter(new StringWriter())));
      assertFalse(out.toString().isBlank());
    }
    assertEquals(
        "usage_error", run("query", "missing", "--references").get("status").getAsString());
    assertEquals(
        "usage_error",
        run("refactor", "name", "missing", "app.amount", "--to", "other", "--apply")
            .get("status")
            .getAsString());
    assertEquals(
        "usage_error",
        run("query", "missing", "app.amount", "--search", "amount").get("status").getAsString());
  }

  private JsonObject run(String... arguments) {
    var out = new StringWriter();
    var err = new StringWriter();
    int code = new CliController().run(arguments, new PrintWriter(out), new PrintWriter(err));
    var result = JsonParser.parseString(out.toString()).getAsJsonObject();
    assertEquals(code, result.get("exitCode").getAsInt());
    assertEquals("", err.toString());
    return result;
  }
}
