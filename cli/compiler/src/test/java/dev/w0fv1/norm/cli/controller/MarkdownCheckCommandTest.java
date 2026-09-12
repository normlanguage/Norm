package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MarkdownCheckCommandTest {
  @TempDir Path directory;

  @Test
  void checksNestedMarkdownAgainstRealDeclarationsAndReportsBrokenReferences() throws Exception {
    Path module = module();
    Path docs = Files.createDirectories(directory.resolve("docs/nested"));
    Path markdown = docs.resolve("guide.md");
    Files.writeString(
        markdown, "@sample.api.Box#1\n@sample.api.Box.code#1\n@{sample.api.amount(Integer)#1}\n");
    assertEquals(0, run(docs.getParent(), module).exitCode());
    Files.writeString(
        markdown,
        "@sample.api.Missing#1\n@sample.api.secret#1\n@sample.hidden.Hidden#1\n@sample.api.amount#1\n@sample.api.Box#2\n");
    var result = run(docs.getParent(), module);
    assertNotEquals(0, result.exitCode());
    assertTrue(result.error().contains("Missing"), result.error());
    assertTrue(result.error().contains("secret"), result.error());
    assertTrue(result.error().contains("Hidden"), result.error());
    assertTrue(result.error().contains("ambiguous"), result.error());
    assertTrue(result.error().contains("version"), result.error());
    assertTrue(result.error().contains("guide.md:1:"), result.error());
  }

  @Test
  void reportsStructuredDiagnosticsAndSkipsGeneratedDirectories() throws Exception {
    Path docs = Files.createDirectories(directory.resolve("docs"));
    for (String name : List.of(".vitepress", "node_modules")) {
      Path hidden = Files.createDirectories(docs.resolve(name));
      Files.writeString(hidden.resolve("ignored.md"), "@github.invalid.api.Type#1");
    }
    Files.writeString(docs.resolve("guide.md"), "😀 @github.sample.api.Type#0");
    var output = new StringWriter();
    var error = new StringWriter();
    int exit =
        new DocsCommand()
            .execute(
                List.of("check", docs.toString(), "--format", "json"),
                new PrintWriter(output),
                new PrintWriter(error));
    assertNotEquals(0, exit);
    assertEquals("", error.toString());
    var result = com.google.gson.JsonParser.parseString(output.toString()).getAsJsonObject();
    assertEquals("docs check", result.get("command").getAsString());
    assertEquals(1, result.getAsJsonArray("diagnostics").size());
    assertTrue(output.toString().contains("positive integer version"));
  }

  @Test
  void observesSourceChangesOnTheNextCheck() throws Exception {
    Path module = module();
    Path docs = Files.createDirectories(directory.resolve("docs"));
    Files.writeString(docs.resolve("guide.md"), "@sample.api.Box#1");
    assertEquals(0, run(docs, module).exitCode());
    Files.writeString(
        module.resolve("api.norm"), "package sample\nvalue Renamed { Integer code }\n");
    var result = run(docs, module);
    assertNotEquals(0, result.exitCode());
    assertTrue(result.error().contains("Box"), result.error());
  }

  private Path module() throws Exception {
    Path root = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        root.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"api\"]) }");
    Files.writeString(
        root.resolve("api.norm"),
        """
        package sample
        value Box { Integer code }
        Integer amount(Integer value) { return value }
        String amount(String value) { return value }
        private Integer secret() { return 1 }
        """);
    Files.writeString(
        root.resolve("hidden.norm"), "package sample\nvalue Hidden { Integer code }\n");
    return root;
  }

  private Result run(Path docs, Path module) {
    var output = new StringWriter();
    var error = new StringWriter();
    int exitCode =
        new DocsCommand()
            .execute(
                List.of("check", docs.toString(), "--module", module.toString()),
                new PrintWriter(output),
                new PrintWriter(error));
    return new Result(exitCode, output.toString(), error.toString());
  }

  private record Result(int exitCode, String output, String error) {}
}
