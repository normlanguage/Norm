package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
final class LiveMigrationBoundaryTest {

  @Test
  void retainsTheLastAdoptedRevisionWhenALaterMigrationFails() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        import std.core.Exception
        @StateMigration(convert: migrateState.function)
        class State { Integer count = 7 }
        Void migrateState(MigrationState state) {}
        Void main() {
          var state = State()
          var tick = 0
          for (tick < 5) {
            if (true) { printLine("old:" + state.count.toString()) }
            state.count = state.count + 1
            tick = tick + 1
          }
        }
        """;
    String first =
        source
            .replace("Integer count", "Integer total")
            .replace("state.count", "state.total")
            .replace("old:", "first:")
            .replace(
                "Void migrateState(MigrationState state) {}",
                "Void migrateState(MigrationState state) { state.transfer(source: \"count\", target: \"total\") state.remove(\"count\") }");
    String second =
        first
            .replace("Integer total", "Integer next")
            .replace("state.total", "state.next")
            .replace("first:", "second:")
            .replace("source: \"count\", target: \"total\"", "source: \"total\", target: \"next\"")
            .replace("state.remove(\"count\")", "state.remove(\"total\")");
    var initialUpdate = compile(first);
    var rejected =
        compile(
            second.replace(
                "state.remove(\"total\")",
                "state.remove(\"total\") printLine(\"rejected\") throw Exception(message: \"reject\")"));
    var repaired = compile(second);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:7")) execution.update(initialUpdate);
              if (line.equals("first:8")) execution.update(rejected);
              if (line.equals("first:10")) execution.update(repaired);
            });
    assertEquals(
        List.of("old:7", "first:8", "rejected", "first:9", "first:10", "second:11"),
        result.lines());
  }

  @Test
  void migratesRenamedAndRetypedStateWithoutRestartingTheActiveFrame() {
    String source =
        """
        class State { Integer amount = 7; Integer obsolete = 9 }
        Void main() {
          var state = State()
          var alias = state
          var tick = 0
          for (tick < 3) {
            printLine("old:" + alias.amount.toString() + ":" + tick.toString())
            tick = tick + 1
          }
        }
        """;
    String updated =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        Void convertState(MigrationState state) {
          state.write(field: State.text.field, value: state.read<Integer>("amount").toString())
          state.remove("amount")
          state.remove("obsolete")
        }
        """
            + source
                .replace(
                    "class State { Integer amount = 7; Integer obsolete = 9 }",
                    "@StateMigration(convert: convertState.function) class State { String text = \"default\" }")
                .replace("old:", "new:")
                .replace("alias.amount.toString()", "alias.text");
    var candidate = compile(updated);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:7:0")) execution.update(candidate);
            });
    assertEquals(List.of("old:7:0", "new:7:1", "new:7:2"), result.lines());
    assertTrue(result.status().pendingLocations().isEmpty(), result.status().toString());
  }

  @Test
  void keepsFieldAssignmentInOneRevisionWhenReceiverEvaluationPublishesAnUpdate() {
    String source =
        """
        class State { Integer first = 0; Integer second = 0 }
        State receiver(State state) { printLine("updating"); state }
        Void main() {
          var state = State()
          receiver(state).first = 1
          printLine(state.first)
          printLine(state.second)
        }
        """;
    var candidate =
        compile(source.replace("receiver(state).first = 1", "receiver(state).second = 2"));
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("updating")) execution.update(candidate);
            });
    assertEquals(List.of("updating", "1", "0"), result.lines());
  }

  @Test
  void waitsForAnOutstandingWriteBeforeConvertingItsReceiverState() {
    String source =
        """
        class State { Integer amount = 1 }
        Integer finish(State state) {
          var step = 0
          for (step < 2) { printLine("pending:" + state.amount.toString()); step = step + 1 }
          5
        }
        Void main() {
          var state = State()
          state.amount = finish(state)
          var tick = 0
          for (tick < 2) { printLine("old:" + state.amount.toString()); tick = tick + 1 }
        }
        """;
    String updated =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        Void convertState(MigrationState state) {
          state.write(field: State.text.field, value: state.read<Integer>("amount").toString())
          state.remove("amount")
        }
        """
            + source
                .replace(
                    "class State { Integer amount = 1 }",
                    "@StateMigration(convert: convertState.function) class State { String text = \"default\" }")
                .replace("state.amount = finish(state)", "state.text = finish(state).toString()")
                .replace("state.amount.toString()", "state.text")
                .replace("old:", "new:");
    var candidate = compile(updated);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("pending:1")) execution.update(candidate);
            });
    assertEquals(List.of("pending:1", "pending:1", "new:5", "new:5"), result.lines());
  }

  @Test
  void retainsAWritableHandleNeededByASuspendedContinuationUntilItsFrameReturns() {
    String source =
        """
        class State { Integer amount = 1 }
        Void work(State state) {
          var handle = State.amount.field.bind(state)
          var tick = 0
          for (tick < 2) { printLine("old:" + state.amount.toString()); tick = tick + 1 }
          handle.write(9)
        }
        Void main() { var state = State(); work(state); printLine(state.amount) }
        """;
    String updated =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        Void convertState(MigrationState state) {
          state.transfer(source: "amount", target: "renamed")
          state.remove("amount")
        }
        """
            + source
                .replace(
                    "class State { Integer amount = 1 }",
                    "@StateMigration(convert: convertState.function) class State { Integer renamed = 0 }")
                .replace("State.amount", "State.renamed")
                .replace("state.amount", "state.renamed")
                .replace("old:", "new:");
    var candidate = compile(updated);
    var result =
        run(
            source,
            (line, execution) -> {
              if (line.equals("old:1")) execution.update(candidate);
            });
    assertEquals(List.of("old:1", "old:1", "9"), result.lines());
  }

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
    var result = dev.w0fv1.norm.testing.NormTestKit.compile(source);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    return result.output().orElseThrow().artifact();
  }

  private record Result(List<String> lines, dev.w0fv1.norm.execution.LiveExecutionStatus status) {}
}
