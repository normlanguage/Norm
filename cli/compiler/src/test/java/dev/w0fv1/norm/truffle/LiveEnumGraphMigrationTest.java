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
final class LiveEnumGraphMigrationTest {
  private static final String SOURCE =
      """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertKind.function)
        enum Kind { Count(Integer amount) }
        Void convertKind(MigrationState state) {
          state.replace(Kind.Count(amount: state.read<Integer>(index: 0)))
        }
        class Node {
          Kind kind
          Node? next = null
          List<Kind> items = []
          Map<Kind, String> labels = Map<>()
          String describe() {
            "old:" + (labels.get(key: Kind.Count(amount: 3)) ?? "missing")
          }
        }
        Void main() {
          var kind = Kind.Count(amount: 3)
          var node = Node(kind: kind)
          node.next = node
          node.items.add(kind)
          node.labels.put(key: kind, value: "three")
          var alias = node
          var count = 0
          for (count < 3) {
            require(condition: alias == node && node.next == node, message: "identity and cycle")
            printLine(node.describe())
            printLine(node.kind)
            printLine(node.items[0])
            printLine(kind)
            count = count + 1
          }
        }
        """;

  @Test
  void replacesRemovedVariantsAcrossLocalsCyclicObjectsAndContainerKeys() {
    String candidate =
        SOURCE
            .replace("enum Kind { Count(Integer amount) }", "enum Kind { Text(String text) }")
            .replace(
                "state.replace(Kind.Count(amount: state.read<Integer>(index: 0)))",
                "state.replace(Kind.Text(text: state.read<Integer>(index: 0).toString()))")
            .replace("Kind.Count(amount: 3)", "Kind.Text(text: \"3\")")
            .replace("old:", "new:");
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
                if (character == '\n' && ++lines == 4)
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
              "old:three",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "new:three",
              "Kind.Text(3)",
              "Kind.Text(3)",
              "Kind.Text(3)",
              "new:three",
              "Kind.Text(3)",
              "Kind.Text(3)",
              "Kind.Text(3)"),
          output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }

  @Test
  void rollsBackCollidingEnumKeysAndRetriesARepairedRevision() {
    String source =
        SOURCE
            .replace(
                "node.labels.put(key: kind, value: \"three\")",
                "node.labels.put(key: kind, value: \"three\") node.labels.put(key: Kind.Count(amount: 4), value: \"four\")")
            .replace(
                "printLine(node.describe())",
                "require(condition: node.labels.size() == 2 && node.labels.get(key: Kind.Count(amount: 4)) == \"four\", message: \"map entries survive rollback\") printLine(node.describe())");
    String repaired =
        source
            .replace("enum Kind { Count(Integer amount) }", "enum Kind { Text(String text) }")
            .replace(
                "state.replace(Kind.Count(amount: state.read<Integer>(index: 0)))",
                "state.replace(Kind.Text(text: state.read<Integer>(index: 0).toString()))")
            .replace("Kind.Count(amount: 3)", "Kind.Text(text: \"3\")")
            .replace("Kind.Count(amount: 4)", "Kind.Text(text: \"4\")")
            .replace("old:", "new:");
    String colliding = repaired.replace("state.read<Integer>(index: 0).toString()", "\"same\"");
    var initial = NormTestKit.compile(source);
    var failure = NormTestKit.compile(colliding);
    var repair = NormTestKit.compile(repaired);
    assertTrue(initial.isSuccess(), initial.diagnostics().toString());
    assertTrue(failure.isSuccess(), failure.diagnostics().toString());
    assertTrue(repair.isSuccess(), repair.diagnostics().toString());
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
                  if (lines == 4) live.update(failure.output().orElseThrow().artifact());
                  if (lines == 8) {
                    assertFalse(live.status().pendingLocations().isEmpty());
                    live.update(repair.output().orElseThrow().artifact());
                  }
                }
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
              "old:three",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "old:three",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "Kind.Count(3)",
              "new:three",
              "Kind.Text(3)",
              "Kind.Text(3)",
              "Kind.Text(3)"),
          output.toString().lines().toList());
      assertTrue(live.status().pendingLocations().isEmpty());
    }
  }
}
