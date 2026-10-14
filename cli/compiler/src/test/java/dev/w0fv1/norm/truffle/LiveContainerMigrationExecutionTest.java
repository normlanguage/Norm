package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
final class LiveContainerMigrationExecutionTest {
  @Test
  void failedLocalInitializerRollsBackRetainedContainerMutation() {
    String source =
        """
        class Ledger { List<Integer> items = [1]; Integer step() { items.size() } }
        Integer initialize(Ledger ledger, Integer divisor) { ledger.items.add(9); 10 / divisor }
        Void main() {
          var ledger = Ledger()
          var count = 0
          for (count < 4) { printLine(ledger.step()); count = count + 1 }
        }
        """;
    String update =
        source
            .replace("var count = 0", "var extra = initialize(ledger, divisor: 0); var count = 0")
            .replace("printLine(ledger.step())", "printLine(ledger.step() + extra - extra)");
    var brokenResult = NormTestKit.compile(update);
    assertTrue(brokenResult.isSuccess(), brokenResult.diagnostics().toString());
    var broken = brokenResult.output().orElseThrow().artifact();
    var repairedResult =
        NormTestKit.compile(
            update.replace("initialize(ledger, divisor: 0)", "initialize(ledger, divisor: 1)"));
    assertTrue(repairedResult.isSuccess(), repairedResult.diagnostics().toString());
    var repaired = repairedResult.output().orElseThrow().artifact();
    try (var execution =
        new TruffleExecutionBackend()
            .prepareLive(NormTestKit.compile(source).output().orElseThrow().artifact())) {
      var lines = new ArrayList<String>();
      var pending = new StringBuilder();
      var writer =
          new Writer() {
            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char character = characters[index];
                if (character == '\n') {
                  lines.add(pending.toString());
                  pending.setLength(0);
                  if (lines.size() == 1) execution.update(broken);
                  if (lines.size() == 3) execution.update(repaired);
                } else if (character != '\r') pending.append(character);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      execution.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(List.of("1", "1", "1", "2"), lines);
      assertTrue(execution.status().pendingLocations().isEmpty());
    }
  }
}
