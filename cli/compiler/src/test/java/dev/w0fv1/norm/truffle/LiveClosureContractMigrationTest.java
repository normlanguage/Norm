package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
final class LiveClosureContractMigrationTest {
  private static final String SOURCE =
      """
      Function<Integer()> acquire() {
        var first = 1
        var second = 2;
        () { first * 10 + second }
      }
      Void main() {
        var retained = acquire()
        var count = 0
        for (count < 3) {
          printLine(retained())
          printLine(acquire()())
          count = count + 1
        }
      }
      """;

  @Test
  void retainsCaptureOrderWithOriginalCallableContract() {
    assertEquals(
        List.of("12", "12", "12", "21", "12", "21"),
        execute(SOURCE.replace("first * 10 + second", "second * 10 + first")));
  }

  @Test
  void reloadsCompatibleClosureBody() {
    assertEquals(
        List.of("12", "12", "102", "102", "102", "102"),
        execute(SOURCE.replace("first * 10 + second", "first * 100 + second")));
  }

  private List<String> execute(String candidate) {
    var initial = NormTestKit.compile(SOURCE);
    var update = NormTestKit.compile(candidate);
    assertTrue(initial.isSuccess(), initial.diagnostics().toString());
    assertTrue(update.isSuccess(), update.diagnostics().toString());
    try (var live =
        new TruffleExecutionBackend().prepareLive(initial.output().orElseThrow().artifact())) {
      var output = new StringBuilder();
      var writer =
          new Writer() {
            private int lines;

            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char character = characters[index];
                output.append(character);
                if (character == '\n' && ++lines == 2)
                  live.update(update.output().orElseThrow().artifact());
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      return output.toString().lines().toList();
    }
  }
}
