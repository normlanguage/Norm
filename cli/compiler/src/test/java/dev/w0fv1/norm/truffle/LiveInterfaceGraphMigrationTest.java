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
final class LiveInterfaceGraphMigrationTest {
  @Test
  void migratesRemovedConformanceAndRebindsRetainedInterfaceLocal() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        interface Readable { Integer read() }
        @StateMigration(convert: convertCell.function)
        class Cell implements Readable {
          Integer value
          Integer read() { value }
        }
        Void convertCell(MigrationState state) {}
        Void main() {
          Readable cell = Cell(value: 7)
          var tick = 0
          for (tick < 3) {
            printLine("old:" + cell.read().toString() + ":" + tick.toString())
            tick = tick + 1
          }
        }
        """;
    String candidate =
        source
            .replace("class Cell implements Readable", "class Cell")
            .replace("Readable cell", "Cell cell")
            .replace("old:", "new:");
    var initial = NormTestKit.compile(source);
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
                if (character == '\n' && ++lines == 1)
                  live.update(update.output().orElseThrow().artifact());
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(List.of("old:7:0", "new:7:1", "new:7:2"), output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }
}
