package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
final class LiveMigrationBoundaryTest {
  @TempDir Path directory;

  @Test
  void defersAnAmbiguousSameRoleLoopUntilTheCurrentActivationReturns() {
    String source =
        """
        Void work() {
          var count = 0
          for (count < 2) { printLine("old:" + count.toString()); count = count + 1 }
        }
        Void main() { work(); work() }
        """;
    var candidate =
        compile(
            source
                .replace("var count = 0", "for (false) { printLine(\"inserted\") } var count = 0")
                .replace("old:", "new:"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:0")) execution.update(candidate);
              if (line.equals("old:1"))
                assertFalse(execution.status().pendingLocations().isEmpty());
            });
    assertEquals(List.of("old:0", "old:1", "new:0", "new:1"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void defersSwappedSameRoleLoopsRatherThanBindingTheActiveLoopToItsSibling() {
    String first =
        "for (count < 2) { printLine(\"first:\" + count.toString()); count = count + 1 }";
    String second =
        "for (count < 4) { printLine(\"second:\" + count.toString()); count = count + 1 }";
    String source =
        "Void work() { var count = 0 " + first + " " + second + " } Void main() { work(); work() }";
    var candidate = compile(source.replace(first + " " + second, second + " " + first));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("first:0")) execution.update(candidate);
              if (line.equals("first:1"))
                assertFalse(execution.status().pendingLocations().isEmpty());
            });
    assertEquals(
        List.of(
            "first:0",
            "first:1",
            "second:2",
            "second:3",
            "second:0",
            "second:1",
            "second:2",
            "second:3"),
        result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void doesNotRebindARetainedClosureToANewSiblingLambda() {
    String source =
        """
        Void main() {
          Function<String()> retained = () { "old:" }
          var count = 0
          for (count < 3) {
            printLine(retained() + count.toString())
            count = count + 1
          }
        }
        """;
    var candidate =
        compile(
            source
                .replace(
                    "Function<String()> retained",
                    "Function<String()> unrelated = () { \"wrong:\" }; Function<String()> retained")
                .replace("old:", "new:"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:0")) execution.update(candidate);
            });
    assertEquals(List.of("old:0", "new:1", "new:2"), result.lines());
  }

  @Test
  void attemptsAFailingNewLocalInitializerOnceAndClearsPendingWhenFrameReturns() {
    String source =
        """
        Void main() {
          var count = 0
          for (count < 4) { printLine("old:" + count.toString()); count = count + 1 }
        }
        """;
    String updated =
        "String added(Integer divisor) { printLine(\"attempt\"); var ignored = 1 / divisor; \"new:\" }\n"
            + source
                .replace("var count = 0", "var label = added(0) var count = 999")
                .replace("\"old:\" + count.toString()", "label + count.toString()");
    var candidate = compile(updated);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:0")) execution.update(candidate);
              if (line.equals("old:1"))
                assertFalse(execution.status().pendingLocations().isEmpty());
            });
    assertEquals(List.of("old:0", "attempt", "old:1", "old:2", "old:3"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void failedFieldMigrationKeepsAliasesAndRetriesOnlyAfterAnotherRevision() {
    String source =
        """
        Integer seed(Integer divisor) { printLine("attempt"); 10 / divisor }
        class Counter { Integer value = 0; Integer step() { value = value + 1; value } }
        Void main() {
          var counter = Counter()
          var alias = counter
          var count = 0
          for (count < 4) { printLine(alias.step()); count = count + 1 }
        }
        """;
    String update =
        source
            .replace("class Counter {", "class Counter { Integer extra = seed(0);")
            .replace("value + 1", "value + extra");
    var broken = compile(update);
    var repaired = compile(update.replace("seed(0)", "seed(1)"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("1")) execution.update(broken);
              if (line.equals("2")) assertFalse(execution.status().pendingLocations().isEmpty());
              if (line.equals("3")) execution.update(repaired);
            });
    assertEquals(List.of("1", "attempt", "2", "3", "attempt", "13"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void correctedRevisionCanEnterInsideAStillRunningFallbackRegion() {
    String source =
        """
        String added(Integer divisor) { printLine("attempt"); var value = 1 / divisor; "new:" }
        Void main() {
          var count = 0
          printLine("ready")
          if (true) {
            for (count < 4) { printLine("old:" + count.toString()); count = count + 1 }
          }
        }
        """;
    String changed =
        source
            .replace("var count = 0", "var label = added(0) var count = 999")
            .replace("\"old:\" + count.toString()", "label + count.toString()");
    var broken = compile(changed);
    var repaired = compile(changed.replace("added(0)", "added(1)"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("ready")) execution.update(broken);
              if (line.equals("old:1")) execution.update(repaired);
            });
    assertEquals(
        List.of("ready", "attempt", "old:0", "old:1", "attempt", "new:2", "new:3"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void rejectsAnIncompatibleReintroductionWhileAnEarlierObjectRemainsLive() {
    String source =
        """
        class Counter { Integer value = 7 }
        Void main() {
          var retained = Counter()
          var count = 0
          for (count < 3) { printLine("old:" + count.toString()); count = count + 1 }
        }
        """;
    String removed =
        source
            .replace("class Counter { Integer value = 7 }", "")
            .replace("var retained = Counter()", "")
            .replace("old:", "removed:");
    var removal = compile(removed);
    var incompatible = compile("class Counter { String value = \"wrong\" }\n" + removed);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:0")) {
                execution.update(removal);
                var failure =
                    assertThrows(
                        IllegalArgumentException.class, () -> execution.update(incompatible));
                assertTrue(failure.getMessage().contains("migration"), failure.getMessage());
              }
            });
    assertEquals(List.of("old:0", "removed:1", "removed:2"), result.lines());
  }

  @Test
  void keepsShadowedLocalStateDistinctAcrossVersions() {
    String source =
        """
        Void main() {
          var value = 19
          var count = 0
          for (count < 3) {
            if (true) { var value = 7; printLine("inner:" + value.toString()) }
            printLine("outer:" + value.toString())
            count = count + 1
          }
        }
        """;
    var candidate =
        compile(source.replace("value = 19", "value = 999").replace("value = 7", "value = 8"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("inner:7")) execution.update(candidate);
            });
    assertEquals(
        List.of("inner:7", "outer:19", "inner:8", "outer:19", "inner:8", "outer:19"),
        result.lines());
  }

  private Result run(String source, BiConsumer<String, LiveExecution> onLine) {
    try (var execution = new TruffleExecutionBackend().prepareLive(compile(source))) {
      var output = new StringBuilder();
      var currentLine = new StringBuilder();
      var writer =
          new Writer() {
            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char character = characters[index];
                output.append(character);
                if (character == '\n') {
                  String line = currentLine.toString();
                  currentLine.setLength(0);
                  onLine.accept(line, execution);
                } else if (character != '\r') currentLine.append(character);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      execution.execute(ExecutionContext.of(new PrintWriter(writer)));
      return new Result(output.toString().lines().toList(), execution.status());
    }
  }

  private CoreArtifact compile(String source) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(directory.resolve("boundaries.norm"), source));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }

  private record Result(List<String> lines, dev.w0fv1.norm.execution.LiveExecutionStatus status) {}
}
