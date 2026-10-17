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
final class LiveEnumIteratorMigrationTest {
  @Test
  void defersVariantReplacementUntilTheTypedCursorExhausts() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertKind.function)
        enum Kind { Count(Integer amount) }
        Void convertKind(MigrationState state) {
          state.replace(Kind.Count(amount: state.read<Integer>(index: 0)))
        }
        Void main() {
          List<Kind> kinds = [Kind.Count(amount: 3), Kind.Count(amount: 4)]
          var count = 0
          for (count < 3) {
            for kind : kinds { printLine("old") printLine(kind) }
            count = count + 1
          }
        }
        """;
    String candidate =
        source
            .replace("enum Kind { Count(Integer amount) }", "enum Kind { Text(String text) }")
            .replace(
                "state.replace(Kind.Count(amount: state.read<Integer>(index: 0)))",
                "state.replace(Kind.Text(text: state.read<Integer>(index: 0).toString()))")
            .replace("Kind.Count(amount: 3)", "Kind.Text(text: \"3\")")
            .replace("Kind.Count(amount: 4)", "Kind.Text(text: \"4\")")
            .replace("printLine(\"old\")", "printLine(\"new\")");
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
          List.of(
              "old",
              "Kind.Count(3)",
              "old",
              "Kind.Count(4)",
              "new",
              "Kind.Text(3)",
              "new",
              "Kind.Text(4)",
              "new",
              "Kind.Text(3)",
              "new",
              "Kind.Text(4)"),
          output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }
}
