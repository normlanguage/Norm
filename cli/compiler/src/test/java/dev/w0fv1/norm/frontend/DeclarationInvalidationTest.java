package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DeclarationInvalidationTest {
  @Test
  void reusesCallersAfterAnImplementationEditAcrossSessions(@TempDir Path directory)
      throws Exception {
    var file = directory.resolve("main.norm");
    var cache = directory.resolve("cache");
    String source =
        "Integer shared() { 41 } Integer first() { shared() } Integer second() { shared() } Void main() { printLine(first() + second()) }";
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.of(file, source)).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var changed = SourceFile.of(file, source.replace("41", "42"));
      var result = compiler.compile(changed);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().analysisReport().analyzedDeclarations());
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertEquals(3, output.state().buildReport().relinkedDefinitions());
      assertEquals(
          ArtifactId.forArtifact(fresh.compile(changed).output().orElseThrow().artifact(), "test"),
          ArtifactId.forArtifact(output.artifact(), "test"));
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(output.artifact(), new java.io.PrintWriter(printed));
      assertEquals("84" + System.lineSeparator(), printed.toString());
    }
  }

  @Test
  void rechecksCallersWhenResolvedContractsChange(@TempDir Path directory) throws Exception {
    String[][] cases = {
      {
        "Integer leaf() { 1 } Integer caller() { leaf() } Void main() { printLine(caller()) }",
        "Integer leaf() { 1 }",
        "String leaf() { \"changed\" }"
      },
      {
        "class Box { public Integer read() { 1 } } Void main() { printLine(Box().read()) }",
        "public Integer read",
        "private Integer read"
      },
      {
        "interface Value {} interface Other {} class Empty implements Value {} T identity<T extends Value>(T value) { value } Void main() { identity(Empty()) }",
        "T extends Value",
        "T extends Other"
      },
      {
        "Integer leaf(Integer input = 1) { input } Void main() { printLine(leaf()) }",
        "input = 1",
        "input"
      },
      {
        "Integer work(Integer callback(Integer value)) { callback(1) } Void main() { printLine(work { value + 1 }) }",
        "callback(Integer value)",
        "callback(Integer changed)"
      },
      {
        "class Parent { Integer item = 1 } class Child extends Parent {} Void main() { Integer value = Child().item; printLine(value) }",
        "Integer item = 1",
        "String item = \"changed\""
      },
      {
        "interface Reader { Integer read() { 1 } } class Value implements Reader {} Void main() { Reader value = Value(); printLine(value.read()) }",
        "Integer read() { 1 }",
        "Integer read()"
      }
    };
    for (int index = 0; index < cases.length; index++) {
      var scenario = cases[index];
      var file = directory.resolve("case" + index + ".norm");
      var cache = directory.resolve("cache" + index);
      try (var compiler = CompilerSession.persistent(cache)) {
        var original = compiler.compile(SourceFile.of(file, scenario[0]));
        assertTrue(original.isSuccess(), index + ": " + original.diagnostics());
      }
      try (var compiler = CompilerSession.persistent(cache);
          var fresh = new CompilerSession()) {
        var source = SourceFile.of(file, scenario[0].replace(scenario[1], scenario[2]));
        var expected = fresh.compile(source);
        var actual = compiler.compile(source);
        assertFalse(expected.isSuccess(), "case must exercise a signature diagnostic: " + index);
        assertEquals(expected.diagnostics(), actual.diagnostics(), "case " + index);
      }
    }
  }

  @Test
  void reusesCallersWhenOnlyADefaultImplementationChanges(@TempDir Path directory)
      throws Exception {
    var file = directory.resolve("main.norm");
    var cache = directory.resolve("cache");
    String original =
        "Integer number(Integer input = 41) { input } Void main() { printLine(number()) }";
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.of(file, original)).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var source = SourceFile.of(file, original.replace("41", "42"));
      var actual = compiler.compile(source);
      assertTrue(actual.isSuccess(), actual.diagnostics().toString());
      var output = actual.output().orElseThrow();
      assertEquals(1, output.state().analysisReport().analyzedDeclarations());
      assertEquals(
          ArtifactId.forArtifact(fresh.compile(source).output().orElseThrow().artifact(), "test"),
          ArtifactId.forArtifact(output.artifact(), "test"));
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(output.artifact(), new java.io.PrintWriter(printed));
      assertEquals("42" + System.lineSeparator(), printed.toString());
    }
  }
}
