package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
final class LiveStructureExecutionTest {
  @TempDir Path directory;

  @Test
  void addsLocalsBranchesAndCallsWithinAnActiveLoopWithoutReplayingState() {
    String source =
        """
        Void main() {
          printLine("init")
          var count = 0
          for (count < 4) {
            printLine("old:" + count.toString())
            count = count + 1
          }
        }
        """;
    var result =
        run(
            source,
            source.replace(
                "printLine(\"old:\" + count.toString())",
                "var label = \"new:\" if (count > 0) { printLine(label + count.toString()) }"));
    assertEquals(List.of("init", "old:0", "new:1", "new:2", "new:3"), result.lines());
  }

  @Test
  void keepsAChangedBlockVersionUntilTheCurrentBlockExecutionReturns() {
    String source =
        """
        Void main() {
          var count = 0
          for (count < 2) {
            printLine("before:old:" + count.toString())
            printLine("after:old:" + count.toString())
            count = count + 1
          }
        }
        """;
    String updated = source.replace("old:", "new:");
    var result = run(source, updated, 1);
    assertEquals(
        List.of("before:old:0", "after:old:0", "before:new:1", "after:new:1"), result.lines());
  }

  @Test
  void initializesNewOuterStateOnceWithoutReplayingExistingDeclarations() {
    String source =
        """
        Void main() {
          printLine("init")
          var count = 0
          for (count < 4) {
            printLine("old:" + count.toString())
            count = count + 1
          }
        }
        """;
    String updated =
        "String added() { printLine(\"added\"); \"new:\" }\n"
            + source
                .replace("var count = 0", "var label = added() var count = 1000")
                .replace("\"old:\" + count.toString()", "label + count.toString()");
    var result = run(source, updated);
    assertEquals(List.of("init", "old:0", "added", "new:1", "new:2", "new:3"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void preservesValuesWhenSourceLocalOrderChanges() {
    String source =
        """
        Void main() {
          printLine("init")
          var count = 0
          var retained = 19
          for (count < 4) {
            printLine("old:" + retained.toString())
            count = count + 1
          }
        }
        """;
    var result =
        run(
            source,
            source
                .replace(
                    "var count = 0\n  var retained = 19", "var retained = 999\n  var count = 999")
                .replace("old:", "new:"));
    assertEquals(List.of("init", "old:19", "new:19", "new:19", "new:19"), result.lines());
  }

  private Result run(String source, String updated) {
    return run(source, updated, 2);
  }

  private Result run(String source, String updated, int updateAfterLine) {
    try (var execution = new TruffleExecutionBackend().prepareLive(compile(source))) {
      var candidate = compile(updated);
      var output = new StringBuilder();
      var writer =
          new Writer() {
            private int lines;

            @Override
            public void write(char[] text, int start, int length) {
              for (int index = start; index < start + length; index++) {
                output.append(text[index]);
                if (text[index] == '\n' && ++lines == updateAfterLine) execution.update(candidate);
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

  private record Result(List<String> lines, dev.w0fv1.norm.execution.LiveExecutionStatus status) {}

  private CoreArtifact compile(String source) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(directory.resolve("live.norm"), source));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      return result.output().orElseThrow().artifact();
    }
  }
}
