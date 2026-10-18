package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoreContentLinkageTest {
  @Test
  void publishedContentSurvivesRelinkingAndPortableReload(@TempDir Path directory)
      throws Exception {
    var coordinate = new ModuleCoordinate("library", 1);
    var file = directory.resolve("library.norm");
    var original =
        "Integer first() { 1 } Integer second() { 1 } Integer sum() { first() + second() }";
    var revisions =
        List.of(
            original,
            original.replace("first() { 1 }", "first() { 2 }"),
            "Integer extra() { 9 } " + original.replace("first() { 1 }", "first() { 2 }"));
    CompiledModule previous = null;
    for (var text : revisions) {
      var source = SourceFile.of(file, text);
      var scope =
          new CompilationScope(
              Map.of(source.id(), new ModuleSourceCoordinate(coordinate, "library.norm")),
              new ModuleGraph(Map.of(coordinate, Set.of())));
      var request =
          new CompilationRequest(
                  new CompilationUnitId(source.id().uri()),
                  scope,
                  source.id(),
                  List.of(source),
                  Set.of(source.id()))
              .asLibrary();
      CompiledModule published;
      try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
        var result = compiler.compileModule(request, coordinate);
        assertTrue(result.compilation().isSuccess(), result.compilation().diagnostics().toString());
        published = result.module().orElseThrow();
      }
      var reloaded = CompiledModule.decode(published.encode());
      assertEquals(published.content().definitions(), reloaded.content().definitions());
      var definitions =
          reloaded.content().definitions().values().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      definition -> definition.origin().definitionName(),
                      definition -> definition.unit().source()));
      if (previous == null) {
        assertEquals(
            definitions.get("first").content().id(), definitions.get("second").content().id());
      } else {
        var earlier =
            previous.content().definitions().values().stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        definition -> definition.origin().definitionName(),
                        definition -> definition.unit().source()));
        assertEquals(earlier.get("sum").content().id(), definitions.get("sum").content().id());
        assertEquals(
            earlier.get("second").content().id(), definitions.get("second").content().id());
        assertNotEquals(
            definitions.get("first").content().id(), definitions.get("second").content().id());
      }
      previous = reloaded;
    }
  }

  @Test
  void immutableExecutablesKeepExactBindingsAcrossUpdates(@TempDir Path directory)
      throws Exception {
    var scenarios =
        List.of(
            new Scenario(
                "Integer first() { 1 } Integer second() { 1 } Integer sum() { first() + second() } Void main() { printLine(sum()) }",
                "first() { 1 }",
                "first() { 2 }",
                "2",
                "3"),
            new Scenario(
                "Integer left(Integer n) { if (n == 0) { return 1 } return right(n - 1) } Integer right(Integer n) { if (n == 0) { return 1 } return left(n - 1) } Void main() { printLine(right(1)) }",
                "left(Integer n) { if (n == 0) { return 1 }",
                "left(Integer n) { if (n == 0) { return 2 }",
                "1",
                "2"),
            new Scenario(
                "class Box { Integer first() { 1 } Integer second() { 1 } } Integer sum(Box value) { value.first() + value.second() } Void main() { printLine(sum(Box())) }",
                "first() { 1 }",
                "first() { 2 }",
                "2",
                "3"),
            new Scenario(
                "Integer first() { 1 } Function<Integer()> deferred(Integer input) { Integer captured = input; () { first() + captured } } Void main() { Function<Integer()> callback = deferred(7); printLine(callback()) }",
                "first() { 1 }",
                "first() { 2 }",
                "8",
                "9"));
    var runtime = new NormRuntime(new TruffleExecutionBackend());
    for (int index = 0; index < scenarios.size(); index++) {
      var scenario = scenarios.get(index);
      var file = directory.resolve("case" + index + ".norm");
      CoreArtifact original;
      try (var compiler = CompilerSession.persistent(directory.resolve("cache" + index))) {
        var result = compiler.compile(SourceFile.of(file, scenario.source()));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
        original = result.output().orElseThrow().artifact();
      }
      try (var compiler = CompilerSession.persistent(directory.resolve("cache" + index))) {
        var result =
            compiler.compile(
                SourceFile.of(
                    file, scenario.source().replace(scenario.before(), scenario.after())));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
        var updated = result.output().orElseThrow().artifact();
        assertNotEquals(original.entryDefinition(), updated.entryDefinition());
        assertOutput(runtime, original, scenario.originalOutput());
        assertOutput(runtime, updated, scenario.updatedOutput());
        assertOutput(runtime, original, scenario.originalOutput());
        assertOutput(runtime, updated, scenario.updatedOutput());
      }
    }
  }

  private static void assertOutput(NormRuntime runtime, CoreArtifact artifact, String expected) {
    var output = new StringWriter();
    runtime.run(artifact, new PrintWriter(output));
    assertEquals(expected + System.lineSeparator(), output.toString());
  }

  private record Scenario(
      String source, String before, String after, String originalOutput, String updatedOutput) {}
}
