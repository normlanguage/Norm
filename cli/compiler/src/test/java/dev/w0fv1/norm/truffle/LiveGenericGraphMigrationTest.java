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
final class LiveGenericGraphMigrationTest {
  @Test
  void replacementReifiesNewGenericArgumentsAndPreservesIdentityAliases() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertBox.function)
        class Box<T> {
          T value
          String describe() { "old" }
        }
        Void convertBox(MigrationState state) {
          state.write(field: Box<Integer>.value.field, value: state.read<Integer>("value"))
        }
        Void main() {
          var box = Box<Integer>(value: 7)
          var alias = box
          List<Box<Integer>> boxes = [box]
          var count = 0
          for (count < 3) {
            require(condition: alias == box && boxes[0] == box, message: "identity aliases")
            require(condition: alias.value == 7, message: "retained typed field")
            printLine(alias.describe())
            printLine(alias.value)
            count = count + 1
          }
        }
        """;
    String candidate =
        source
            .replace("class Box<T>", "class Box<T, U>")
            .replace("T value", "T value; U label")
            .replace("\"old\"", "\"new\"")
            .replace("printLine(alias.value)", "printLine(alias.label)")
            .replace(
                "state.write(field: Box<Integer>.value.field, value: state.read<Integer>(\"value\"))",
                "state.replace(Box<Integer, String>(value: state.read<Integer>(\"value\"), label: \"ready\"))")
            .replace("Box<Integer>", "Box<Integer, String>")
            .replace(
                "Box<Integer, String>(value: 7)",
                "Box<Integer, String>(value: 7, label: \"ready\")");
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
      assertEquals(
          List.of("old", "7", "new", "ready", "new", "ready"), output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }
}
