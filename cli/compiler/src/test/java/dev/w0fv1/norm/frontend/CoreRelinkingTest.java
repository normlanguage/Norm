package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoreRelinkingTest {
  @Test
  void preservesExactTargetsAndRecursiveGroupsAcrossSessions(@TempDir Path directory)
      throws Exception {
    var cases =
        java.util.List.of(
            new Scenario(
                "Integer first() { 1 } Integer second() { 1 } Integer sum() { first() + second() } Void main() { printLine(sum()) }",
                "first() { 1 }",
                "first() { 2 }",
                "3",
                1,
                2),
            new Scenario(
                "Integer left(Integer n) { if (n == 0) { return 1 } return right(n - 1) } Integer right(Integer n) { if (n == 0) { return 1 } return left(n - 1) } Void main() { printLine(right(1)) }",
                "left(Integer n) { if (n == 0) { return 1 }",
                "left(Integer n) { if (n == 0) { return 2 }",
                "2",
                1,
                2),
            new Scenario(
                "class Box { Integer first() { 1 } Integer second() { 1 } } Integer sum(Box value) { value.first() + value.second() } Void main() { printLine(sum(Box())) }",
                "first() { 1 }",
                "first() { 2 }",
                "3",
                4,
                2));
    for (int index = 0; index < cases.size(); index++) {
      var scenario = cases.get(index);
      var file = directory.resolve("case" + index + ".norm");
      var cache = directory.resolve("cache" + index);
      try (var compiler = CompilerSession.persistent(cache)) {
        var result = compiler.compile(SourceFile.of(file, scenario.source()));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
      }
      try (var compiler = CompilerSession.persistent(cache);
          var fresh = new CompilerSession()) {
        var source =
            SourceFile.of(file, scenario.source().replace(scenario.before(), scenario.after()));
        var result = compiler.compile(source);
        assertTrue(result.isSuccess(), result.diagnostics().toString());
        var output = result.output().orElseThrow();
        assertEquals(
            ArtifactId.forArtifact(fresh.compile(source).output().orElseThrow().artifact(), "test"),
            ArtifactId.forArtifact(output.artifact(), "test"),
            "case " + index);
        assertEquals(
            scenario.converted(),
            output.state().buildReport().convertedDefinitions(),
            "case " + index);
        assertEquals(
            scenario.relinked(),
            output.state().buildReport().relinkedDefinitions(),
            "case " + index);
        var printed = new java.io.StringWriter();
        new dev.w0fv1.norm.runtime.NormRuntime()
            .run(output.artifact(), new java.io.PrintWriter(printed));
        assertEquals(
            scenario.expected() + System.lineSeparator(), printed.toString(), "case " + index);
      }
    }
  }

  private record Scenario(
      String source, String before, String after, String expected, int converted, int relinked) {}
}
