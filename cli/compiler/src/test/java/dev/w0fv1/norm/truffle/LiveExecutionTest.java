package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
final class LiveExecutionTest {
  @TempDir Path directory;

  @Test
  void changesActiveLoopWithoutResettingLocalsOrReplayingInitialization() {
    String source =
        """
        Void main() {
          printLine("init")
          var count = 0
          for (count < 5) {
            printLine("old:" + count.toString())
            count = count + 1
          }
        }
        """;
    var execution = prepare(compile(source));
    var updated = compile(source.replace("old:", "hellonorm:"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 3) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(
        List.of("init", "old:0", "old:1", "hellonorm:2", "hellonorm:3", "hellonorm:4"),
        output.lines());
  }

  @Test
  void retainsNestedLoopPositionsAndUpdatesOrdinaryAssignments() {
    String source =
        """
        Void main() {
          var outer = 0
          var total = 0
          for (outer < 2) {
            var inner = 0
            for (inner < 3) {
              total = total + 1
              printLine("old:" + outer.toString() + ":" + inner.toString() + ":" + total.toString())
              inner = inner + 1
            }
            outer = outer + 1
          }
        }
        """;
    var execution = prepare(compile(source));
    var updated = compile(source.replace("old:", "new:").replace("total + 1", "total + 10"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 2) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(
        List.of("old:0:0:1", "old:0:1:2", "new:0:2:12", "new:1:0:22", "new:1:1:32", "new:1:2:42"),
        output.lines());
  }

  @Test
  void retainsIteratorAndDoesNotEvaluateIterableAgain() {
    String source =
        """
        List<Integer> values() { printLine("factory"); [10, 20, 30, 40] }
        Void main() {
          for value, index : values() {
            printLine("old:" + index.toString() + ":" + value.toString())
          }
        }
        """;
    var execution = prepare(compile(source));
    var updated = compile(source.replace("old:", "new:"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 3) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(
        List.of("factory", "old:0:10", "old:1:20", "new:2:30", "new:3:40"), output.lines());
  }

  @Test
  void rebindsChangedCalleeWithoutConflatingEqualContentDeclarations() {
    String source =
        """
        Integer first() { 1 }
        Integer second() { 1 }
        Void main() {
          var count = 0
          for (count < 4) {
            printLine(first().toString() + ":" + second().toString())
            count = count + 1
          }
        }
        """;
    var execution = prepare(compile(source));
    var updated = compile(source.replace("second() { 1 }", "second() { 27 }"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 2) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(List.of("1:1", "1:1", "1:27", "1:27"), output.lines());
  }

  @Test
  void acceptsUnusedLocalTypeChangesAndLaterRevisions() {
    String source =
        """
        Void main() {
          var preserved = 7
          var count = 0
          for (count < 5) {
            printLine("old:" + count.toString())
            count = count + 1
          }
        }
        """;
    var execution = prepare(compile(source));
    var changed =
        compile(
            source.replace("preserved = 7", "preserved = \"wrong\"").replace("old:", "middle:"));
    var updated = compile(source.replace("old:", "new:"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 2) execution.update(changed);
              if (line == 3) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(List.of("old:0", "old:1", "middle:2", "new:3", "new:4"), output.lines());
  }

  @Test
  void preservesFinallyAndContinueBehavior() {
    String source =
        """
        Void main() {
          var count = 0
          try {
            for (count < 4) {
              count = count + 1
              if (count == 2) { continue }
              printLine("old:" + count.toString())
            }
          } finally { printLine("finished") }
        }
        """;
    var execution = prepare(compile(source));
    var updated = compile(source.replace("old:", "new:"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 1) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(List.of("old:1", "new:3", "new:4", "finished"), output.lines());
  }

  @Test
  void preservesClosureCaptureAndDeclarationBindingAcrossSourceOffsets() {
    String source =
        """
        String label() { "old:" }
        Function<String()> deferred(Integer captured) { () { label() + captured.toString() } }
        Void main() {
          var callback = deferred(19)
          var count = 0
          for (count < 4) {
            printLine(callback())
            count = count + 1
          }
        }
        """;
    var execution = prepare(compile(source));
    var updated =
        compile("Integer unrelated() { 100 }\n" + source.replace("old:", "much-longer-new:"));
    var output =
        new UpdatingWriter(
            line -> {
              if (line == 2) execution.update(updated);
            });
    execution.execute(ExecutionContext.of(new PrintWriter(output)));
    assertEquals(
        List.of("old:19", "old:19", "much-longer-new:19", "much-longer-new:19"), output.lines());
  }

  private CoreArtifact compile(String source) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(directory.resolve("live.norm"), source));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }

  private static LiveExecution prepare(CoreArtifact artifact) {
    return new TruffleExecutionBackend().prepareLive(artifact);
  }

  private static final class UpdatingWriter extends Writer {
    private final StringBuilder output = new StringBuilder();
    private final IntConsumer onLine;
    private int lines;

    UpdatingWriter(IntConsumer onLine) {
      this.onLine = onLine;
    }

    @Override
    public void write(char[] characters, int offset, int length) {
      for (int index = offset; index < offset + length; index++) {
        char character = characters[index];
        output.append(character);
        if (character == '\n') onLine.accept(++lines);
      }
    }

    List<String> lines() {
      return output.toString().lines().toList();
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
