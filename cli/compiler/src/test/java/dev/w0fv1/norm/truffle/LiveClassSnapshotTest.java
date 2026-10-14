package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LiveClassSnapshotTest {
  @Test
  void preservesTheOwningSchemaThroughFieldsAndTheirOwnerDescriptors() {
    String source =
        """
      import std.runtime.StateMigration
      import std.runtime.MigrationState
      import std.annotation.FieldTarget
      import std.annotation.RuntimeRetention
      import std.core.Exception
      annotation Label implements FieldTarget, RuntimeRetention { String text }
      @StateMigration(convert: convertState.function)
      class State {
        @Label(text: "old") Integer first = 1
        Integer method(Integer amount) { return amount + 1 }
      }
      Void convertState(MigrationState state) {}
      Void main() {
        var frozen = State.class.snapshot()
        var live = State.class
        var tick = 0
        for tick < 3 {
          var field = frozen.fields()[0]
          var label = field.annotation<Label>() ?? throw Exception(message: "Missing field label")
          var method = frozen.functions()[0]
          var owner = method.owner() ?? throw Exception(message: "Missing method owner")
          String visibility = if field.isPublic() { "public" } else { "private" }
          printLine(field.name() + ":" + field.owner().fields()[0].name() + ":" + live.fields()[0].name()
            + ":" + label.text + ":" + visibility + ":" + method.parameters()[1].type().name()
            + ":" + live.functions()[0].parameters()[1].type().name()
            + ":" + owner.fields()[0].name() + ":" + frozen.constructors()[0].owner().fields()[0].name())
          tick = tick + 1
        }
      }
      """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace(
                    "@Label(text: \"old\") Integer first = 1",
                    "@Label(text: \"new\") private String second = \"new\"")
                .replace(
                    "Integer method(Integer amount) { return amount + 1 }",
                    "String method(String text) { return text }"));
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
                  lines.add(buffer.toString());
                  buffer.setLength(0);
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
      assertEquals(
          List.of(
              "first:first:first:old:public:Integer:Integer:first:first",
              "first:first:second:old:public:Integer:String:first:first",
              "first:first:second:old:public:Integer:String:first:first"),
          lines);
      assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
    }
  }
}
