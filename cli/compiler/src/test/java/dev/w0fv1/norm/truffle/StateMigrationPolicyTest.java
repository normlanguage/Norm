package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class StateMigrationPolicyTest {
  @Test
  void declaresATypedConverterInNormSource() {
    var compilation =
        NormTestKit.compile(
            """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertState.function)
        class State { Integer total = 0 }
        Void convertState(MigrationState state) {
          if (state.contains("count")) {
            Integer previous = state.read<Integer>("count")
            state.write(field: State.total.field, value: previous)
            state.remove("count")
          }
        }
        Void main() { printLine(State(total: 1).total) }
        """);
    assertTrue(compilation.isSuccess(), compilation.diagnostics().toString());
  }

  @Test
  void rejectsAConverterWritingAnIncompatibleDeclaredFieldType() {
    var compilation =
        NormTestKit.compile(
            """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: convertState.function)
        class State { Integer total = 0 }
        Void convertState(MigrationState state) { state.write(field: State.total.field, value: "wrong") }
        Void main() { printLine(State().total) }
        """);
    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(
                value -> value.message().contains("String") && value.message().contains("Integer")),
        compilation.diagnostics().toString());
  }

  @Test
  void oneConverterPreservesReifiedGenericStateAndInterfaceWitnesses() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        interface Read<T> { T get() }
        @StateMigration(convert: convertBox.function)
        class Box<T> implements Read<T> {
          T value
          T get() { value }
        }
        Void convertBox(MigrationState state) {
          if (state.contains("value")) {
            state.transfer(source: "value", target: "item")
            state.remove("value")
          }
        }
        Void main() {
          var number = Box<Integer>(value: 7)
          var text = Box<String>(value: "Norm")
          var alias = number
          Read<Integer> numberView = number
          Read<String> textView = text
          var count = 0
          for (count < 3) {
            require(condition: alias == number, message: "migration replaced object identity")
            printLine("old:" + count.toString() + ":" + numberView.get().toString() + ":" + textView.get())
            count = count + 1
          }
        }
        """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace("T value", "T item")
                .replace("T get() { value }", "T get() { item }")
                .replace("Box<Integer>(value:", "Box<Integer>(item:")
                .replace("Box<String>(value:", "Box<String>(item:")
                .replace("old:", "new:"));
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    assertEquals(
        java.util.List.of("old:0:7:Norm", "new:1:7:Norm", "new:2:7:Norm"),
        run(
            before.output().orElseThrow().artifact(),
            after.output().orElseThrow().artifact(),
            "old:0:7:Norm"));
  }

  @Test
  void migratesTheStateOfAnAnnotationInstanceWithoutResettingItsCounter() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        import std.annotation.FunctionInterceptor
        import std.annotation.RuntimeRetention
        @StateMigration(convert: convertCounter.function)
        annotation Counter implements FunctionInterceptor, RuntimeRetention {
          Integer count = 40
          Void before(FunctionContext context) {
            count = count + 1
            printLine("old-annotation:" + count.toString())
          }
        }
        Void convertCounter(MigrationState state) {
          if (state.contains("count")) {
            state.transfer(source: "count", target: "calls")
            state.remove("count")
          }
        }
        @Counter
        Void work() {}
        Void main() {
          var tick = 0
          for (tick < 5) { work(); tick = tick + 1 }
        }
        """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace("Integer count = 40", "Integer calls = 40")
                .replace("count = count + 1", "calls = calls + 1")
                .replace("count.toString()", "calls.toString()")
                .replace("old-annotation:", "new-annotation:"));
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    var later =
        NormTestKit.compile(
            source
                .replace("Integer count = 40", "Integer calls = 40")
                .replace("count = count + 1", "calls = calls + 1")
                .replace("count.toString()", "calls.toString()")
                .replace("old-annotation:", "later-annotation:"));
    assertTrue(later.isSuccess(), later.diagnostics().toString());
    assertEquals(
        java.util.List.of(
            "old-annotation:41",
            "new-annotation:42",
            "later-annotation:43",
            "later-annotation:44",
            "later-annotation:45"),
        run(
            before.output().orElseThrow().artifact(),
            java.util.Map.of(
                "old-annotation:41", after.output().orElseThrow().artifact(),
                "new-annotation:42", later.output().orElseThrow().artifact())));
  }

  @Test
  void preservesExplicitConfigurationChangesAcrossLaterCodeUpdates() {
    String source =
        """
        import std.annotation.FunctionInterceptor
        import std.annotation.RuntimeRetention
        annotation Counter implements FunctionInterceptor, RuntimeRetention {
          Integer count
          Void before(FunctionContext context) {
            count = count + 1
            printLine("old-configuration:" + count.toString())
          }
        }
        @Counter(count: 40)
        Void work() {}
        Void main() {
          var tick = 0
          for (tick < 5) { work(); tick = tick + 1 }
        }
        """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace("count: 40", "count: 99")
                .replace("old-configuration:", "new-configuration:"));
    var later =
        NormTestKit.compile(
            source
                .replace("count: 40", "count: 99")
                .replace("old-configuration:", "later-configuration:"));
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    assertTrue(later.isSuccess(), later.diagnostics().toString());
    assertEquals(
        java.util.List.of(
            "old-configuration:41",
            "new-configuration:100",
            "later-configuration:101",
            "later-configuration:102",
            "later-configuration:103"),
        run(
            before.output().orElseThrow().artifact(),
            java.util.Map.of(
                "old-configuration:41", after.output().orElseThrow().artifact(),
                "new-configuration:100", later.output().orElseThrow().artifact())));
  }

  @Test
  void migratesInheritedFieldsUsingTheirParentTypeArguments() {
    String source =
        """
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        interface Read<T> { T get() }
        @StateMigration(convert: convertBase.function)
        class Base<T> implements Read<T> {
          T value
          Base(T value) { this.value = value }
          T get() { value }
        }
        @StateMigration(convert: convertBase.function)
        class Derived<T> extends Base<String> {
          T tag
          Derived(T tag) { super(value: "base"); this.tag = tag }
          T label() { tag }
        }
        Void convertBase(MigrationState state) {
          if (state.contains("value")) {
            state.transfer(source: "value", target: "item")
            state.remove("value")
          }
        }
        Void main() {
          var number = Derived<Integer>(tag: 7)
          var text = Derived<String>(tag: "marker")
          var alias = number
          Read<String> numberView = number
          Read<String> textView = text
          var count = 0
          for (count < 3) {
            require(condition: alias == number, message: "derived identity changed")
            printLine("old:" + count.toString() + ":" + numberView.get() + ":" + number.label().toString() + ":" + textView.get() + ":" + text.label())
            count = count + 1
          }
        }
        """;
    var before = NormTestKit.compile(source);
    var after =
        NormTestKit.compile(
            source
                .replace("T value\n", "T item\n")
                .replace("this.value = value", "this.item = value")
                .replace("T get() { value }", "T get() { item }")
                .replace("old:", "new:"));
    assertTrue(before.isSuccess(), before.diagnostics().toString());
    assertTrue(after.isSuccess(), after.diagnostics().toString());
    assertEquals(
        java.util.List.of(
            "old:0:base:7:base:marker", "new:1:base:7:base:marker", "new:2:base:7:base:marker"),
        run(
            before.output().orElseThrow().artifact(),
            after.output().orElseThrow().artifact(),
            "old:0:base:7:base:marker"));
  }

  private static java.util.List<String> run(
      dev.w0fv1.norm.core.CoreArtifact before,
      dev.w0fv1.norm.core.CoreArtifact after,
      String updateOnLine) {
    return run(before, java.util.Map.of(updateOnLine, after));
  }

  private static java.util.List<String> run(
      dev.w0fv1.norm.core.CoreArtifact before,
      java.util.Map<String, dev.w0fv1.norm.core.CoreArtifact> updates) {
    try (var execution = new TruffleExecutionBackend().prepareLive(before)) {
      var lines = new java.util.ArrayList<String>();
      var pending = new StringBuilder();
      var output =
          new java.io.Writer() {
            @Override
            public void write(char[] characters, int offset, int length) {
              for (int index = offset; index < offset + length; index++) {
                char value = characters[index];
                if (value == '\n') {
                  lines.add(pending.toString());
                  pending.setLength(0);
                  var update = updates.get(lines.getLast());
                  if (update != null) execution.update(update);
                } else if (value != '\r') pending.append(value);
              }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      execution.execute(
          dev.w0fv1.norm.execution.ExecutionContext.of(new java.io.PrintWriter(output)));
      assertTrue(execution.status().pendingLocations().isEmpty(), execution.status().toString());
      return java.util.List.copyOf(lines);
    }
  }
}
