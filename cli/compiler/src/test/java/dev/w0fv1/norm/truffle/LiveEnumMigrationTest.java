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

final class LiveEnumMigrationTest {
  @ParameterizedTest
  @ValueSource(strings = {"T extends Marker = Other", "T = String"})
  void retainsReifiedEnumsAcrossDefaultChangesAndBoundRelaxation(String parameter) {
    String source =
        """
      interface Marker {}
      class Item implements Marker {}
      class Other implements Marker {}
      enum Event<T extends Marker = Item> { Value(T payload) }
      String label(Event<Item> event) {
        return switch event { case Value(Item payload) { break "value" } }
      }
      Void main() {
        var retained = Event<Item>.Value(payload: Item())
        var tick = 0
        for tick < 3 {
          printLine("old:" + tick.toString() + ":" + label(event: retained))
          tick = tick + 1
        }
      }
      """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source.replace("T extends Marker = Item", parameter).replace("old:", "new:"));
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    try (var execution =
        new TruffleExecutionBackend().prepareLive(before.output().orElseThrow().artifact())) {
      var lines = new ArrayList<String>();
      var buffer = new StringBuilder();
      var output =
          new Writer() {
            @Override
            public void write(char[] values, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char value = values[index];
                if (value == '\n') {
                  String line = buffer.toString();
                  buffer.setLength(0);
                  lines.add(line);
                  if (lines.size() == 1) execution.update(after.output().orElseThrow().artifact());
                } else if (value != '\r') buffer.append(value);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      execution.execute(ExecutionContext.of(new PrintWriter(output)));
      assertEquals(3, lines.size());
      assertTrue(lines.get(0).startsWith("old:0:"), lines.toString());
      assertTrue(lines.get(1).startsWith("new:1:"), lines.toString());
      assertTrue(lines.get(2).startsWith("new:2:"), lines.toString());
      assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void convertsARemovedVariantAndChangedPayloadWithoutRestartingTheLoop(boolean nullablePayload) {
    String source =
        """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      @StateMigration(convert: migrateEvent.function)
      enum Event { Previous(Integer amount), Empty }
      class Holder { Event event }
      Void migrateEvent(MigrationState state) {}
      String label(Event event) {
        return switch event {
          case Previous(Integer amount) { break amount.toString() }
          case Empty { break "empty" }
        }
      }
      Void main() {
        var retained = Event.Previous(amount: 7)
        var holder = Holder(event: retained)
        var tick = 0
        for tick < 3 {
          printLine("old:" + tick.toString() + ":" + label(event: holder.event))
          tick = tick + 1
        }
      }
      """;
    var before = NormTestKit.compile(source);
    String candidate =
        source
            .replace(
                "enum Event { Previous(Integer amount), Empty }",
                nullablePayload
                    ? "enum Event { Current(String? text), Empty }"
                    : "enum Event { Current(String text), Empty }")
            .replace(
                "Void migrateEvent(MigrationState state) {}",
                """
          Void migrateEvent(MigrationState state) {
            require(condition: state.variant() == "Previous", message: "previous variant")
            require(condition: state.type().name() == "Event", message: "previous type")
            state.replace(value: Event.Current(text: state.read<Integer>(index: 0).toString()))
          }
          """)
            .replace(
                "case Previous(Integer amount) { break amount.toString() }",
                nullablePayload
                    ? "case Current(String? text) { break text ?? \"none\" }"
                    : "case Current(String text) { break text }")
            .replace("Event.Previous(amount: 7)", "Event.Current(text: \"7\")")
            .replace("old:", "new:");
    if (nullablePayload)
      candidate =
          candidate.replace(
              "Event.Current(text: state.read<Integer>(index: 0).toString())",
              "Event.Current(text: null)");
    var after = NormTestKit.compile(candidate);
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    try (var execution =
        new TruffleExecutionBackend().prepareLive(before.output().orElseThrow().artifact())) {
      var lines = new ArrayList<String>();
      var buffer = new StringBuilder();
      var output =
          new Writer() {
            @Override
            public void write(char[] values, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char value = values[index];
                if (value == '\n') {
                  String line = buffer.toString();
                  buffer.setLength(0);
                  lines.add(line);
                  if (line.equals("old:0:7"))
                    execution.update(after.output().orElseThrow().artifact());
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
          nullablePayload
              ? List.of("old:0:7", "new:1:none", "new:2:none")
              : List.of("old:0:7", "new:1:7", "new:2:7"),
          lines);
      assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
    }
  }
}
