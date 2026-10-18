package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
final class LiveDispatchExecutionTest {
  @Test
  void retainedObjectCanDispatchToAPreviouslyUnusedMethod(@TempDir Path directory) {
    String source =
        """
        class Counter {
          Integer value = 0
          Integer first() { value = value + 1; value }
          Integer second() { value = value + 10; value }
        }
        Void main() {
          var counter = Counter()
          var count = 0
          for (count < 4) {
            printLine(counter.first())
            count = count + 1
          }
        }
        """;
    try (var compiler = new CompilerSession()) {
      var file = directory.resolve("dispatch.norm");
      var initialResult = compiler.compile(SourceFile.of(file, source));
      assertTrue(initialResult.isSuccess(), initialResult.diagnostics().toString());
      var initial = initialResult.output().orElseThrow().artifact();
      var candidateResult =
          compiler.compile(
              SourceFile.of(file, source.replace("counter.first()", "counter.second()")));
      assertTrue(candidateResult.isSuccess(), candidateResult.diagnostics().toString());
      var candidate = candidateResult.output().orElseThrow().artifact();
      var live = new TruffleExecutionBackend().prepareLive(initial);
      var output = new StringBuilder();
      var writer =
          new Writer() {
            private int lines;

            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char character = characters[index];
                output.append(character);
                if (character == '\n' && ++lines == 2) {
                  live.update(candidate);
                }
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(List.of("1", "2", "12", "22"), output.toString().lines().toList());
    }
  }
}
