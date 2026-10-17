package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class LiveSnapshotReturnMigrationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bindsSnapshotResultsToTheInvocationConsumer(boolean reflective) {
    String source =
        """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: migrateEvent.function)
      enum Event { Previous(Integer amount) }
      Void migrateEvent(MigrationState state) {}
      Event produce() { return Event.Previous(amount: 7) }
      Void main() {
        var frozen = produce.function.snapshot()
        var tick = 0
        for tick < 3 {
          String text = switch frozen() {
            case Previous(Integer amount) { break amount.toString() }
          }
          printLine("old:" + tick.toString() + ":" + text)
          tick = tick + 1
        }
      }
      """;
    if (reflective) {
      source =
          source
              .replace(
                  "import std.runtime.StateMigration",
                  "import std.json.invokeJson\nimport std.runtime.StateMigration")
              .replace("Previous(Integer amount)", "Previous")
              .replace("Event.Previous(amount: 7)", "Event.Previous")
              .replaceAll(
                  "String text = switch frozen\\(\\) \\{\\s*case Previous \\{ break amount.toString\\(\\) \\}\\s*\\}",
                  "String text = invokeJson(operation: frozen, arguments: \"{}\")");
    }
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace(
                    "enum Event { Previous(Integer amount) }",
                    "enum Event { Current(String text) }")
                .replace(
                    "Void migrateEvent(MigrationState state) {}",
                    "Void migrateEvent(MigrationState state) { state.replace(value: Event.Current(text: state.read<Integer>(index: 0).toString())) }")
                .replace(
                    "Event produce() { return Event.Previous(amount: 7) }",
                    "Event produce() { return Event.Current(text: \"different\") }")
                .replace(
                    "case Previous(Integer amount) { break amount.toString() }",
                    "case Current(String text) { break text }")
                .replace("old:", "new:"));
    if (reflective) {
      after =
          NormTestKit.compile(
              source
                  .replace("enum Event { Previous }", "enum Event { Current }")
                  .replace(
                      "Void migrateEvent(MigrationState state) {}",
                      "Void migrateEvent(MigrationState state) { state.replace(value: Event.Current) }")
                  .replace(
                      "Event produce() { return Event.Previous }",
                      "Event produce() { return Event.Current }")
                  .replace("old:", "new:"));
    }
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    var candidate = after;
    try (var execution =
        new TruffleExecutionBackend().prepareLive(before.output().orElseThrow().artifact())) {
      var lines = new ArrayList<String>();
      var statuses = new ArrayList<String>();
      var buffer = new StringBuilder();
      var output =
          new Writer() {
            @Override
            public void write(char[] values, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char value = values[index];
                if (value == '\n') {
                  lines.add(buffer.toString());
                  statuses.add(execution.status().toString());
                  buffer.setLength(0);
                  if (lines.size() == 1)
                    execution.update(candidate.output().orElseThrow().artifact());
                } else if (value != '\r') buffer.append(value);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      execution.execute(ExecutionContext.of(new PrintWriter(output)));
      assertEquals(
          reflective
              ? List.of("old:0:\"Previous\"", "new:1:\"Previous\"", "new:2:\"Previous\"")
              : List.of("old:0:7", "new:1:7", "new:2:7"),
          lines,
          statuses.toString());
      assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
    }
  }
}
