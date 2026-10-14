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
final class LiveValueKeyMigrationTest {
  private static final String SOURCE =
      """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: convertKey.function)
      value Key { Integer amount }
      Void convertKey(MigrationState state) {
        state.write(field: Key.amount.field, value: state.read<Integer>("amount"))
      }
      class Ledger {
        Map<Key, String> entries = Map<>()
        Set<Key> keys = Set<>()
        Void seed() {
          entries.put(key: Key(amount: 1), value: "one")
          entries.put(key: Key(amount: 2), value: "two")
          keys.add(Key(amount: 1))
          keys.add(Key(amount: 2))
        }
        String summary() {
          "old:" + (entries.get(key: Key(amount: 1)) ?? "missing") + ":" +
          (entries.get(key: Key(amount: 2)) ?? "missing") + ":" + keys.size().toString()
        }
      }
      Void main() {
        var ledger = Ledger()
        ledger.seed()
        var alias = ledger
        var count = 0
        for (count < 4) {
          printLine(alias.summary())
          count = count + 1
        }
      }
      """;

  @Test
  void migratesValueKeysAndReindexesMapAndSetTogether() {
    assertEvolution(
        candidate(false),
        null,
        List.of("old:one:two:2", "new:one:two:2", "new:one:two:2", "new:one:two:2"));
  }

  @Test
  void keyCollisionRollsBackAndARepairedConverterCanRetry() {
    assertEvolution(
        candidate(true),
        candidate(false),
        List.of("old:one:two:2", "old:one:two:2", "old:one:two:2", "new:one:two:2"));
  }

  @Test
  void retainedSnapshotDefersKeyEvolutionUntilItsOldContainerViewIsReleased() {
    String source =
        SOURCE
            .replace(
                "var alias = ledger",
                "var alias = ledger; Function<String()>? frozen = ledger.summary.snapshot()")
            .replace(
                "printLine(alias.summary())",
                "printLine(alias.summary()); if frozen != null { printLine(frozen()); frozen = null }");
    String update =
        candidate(false)
            .replace(
                "var alias = ledger",
                "var alias = ledger; Function<String()>? frozen = ledger.summary.snapshot()")
            .replace(
                "printLine(alias.summary())",
                "printLine(alias.summary()); if frozen != null { printLine(frozen()); frozen = null }");
    assertEvolution(
        source,
        update,
        null,
        List.of(
            "old:one:two:2", "old:one:two:2", "new:one:two:2", "new:one:two:2", "new:one:two:2"));
  }

  @Test
  void activeTypedIteratorDefersReindexingUntilItsCursorIsExhausted() {
    String source =
        SOURCE.replace(
            "printLine(alias.summary())", "for key : ledger.keys { printLine(alias.summary()) }");
    String update =
        candidate(false)
            .replace(
                "printLine(alias.summary())",
                "for key : ledger.keys { printLine(alias.summary()) }");
    assertEvolution(
        source,
        update,
        null,
        List.of(
            "old:one:two:2",
            "old:one:two:2",
            "new:one:two:2",
            "new:one:two:2",
            "new:one:two:2",
            "new:one:two:2",
            "new:one:two:2",
            "new:one:two:2"));
  }

  private static String candidate(boolean collision) {
    return SOURCE
        .replace("old:", "new:")
        .replace("value Key { Integer amount }", "value Key { String text }")
        .replace(
            "state.write(field: Key.amount.field, value: state.read<Integer>(\"amount\"))",
            "state.write(field: Key.text.field, value: "
                + (collision ? "\"same\"" : "state.read<Integer>(\"amount\").toString()")
                + "); state.remove(\"amount\")")
        .replace("Key(amount: 1)", "Key(text: \"1\")")
        .replace("Key(amount: 2)", "Key(text: \"2\")");
  }

  private static void assertEvolution(String update, String repair, List<String> expected) {
    assertEvolution(SOURCE, update, repair, expected);
  }

  private static void assertEvolution(
      String source, String update, String repair, List<String> expected) {
    var initial = NormTestKit.compile(source);
    var candidate = NormTestKit.compile(update);
    var repaired = repair == null ? candidate : NormTestKit.compile(repair);
    assertTrue(initial.isSuccess(), initial.diagnostics().toString());
    assertTrue(candidate.isSuccess(), candidate.diagnostics().toString());
    assertTrue(repaired.isSuccess(), repaired.diagnostics().toString());
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
                if (character == '\n') {
                  lines++;
                  if (lines == 1) live.update(candidate.output().orElseThrow().artifact());
                  if (lines == 3 && repair != null)
                    live.update(repaired.output().orElseThrow().artifact());
                }
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      live.execute(ExecutionContext.of(new PrintWriter(writer)));
      assertEquals(expected, output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }
}
