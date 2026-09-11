package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.testing.NormTestKit;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ContextualLambdaExecutionTest {
  @Test
  void resolvesInheritedOverloadsAlongsideOverridesAndMethodReferences() {
    assertEquals(
        String.join(System.lineSeparator(), "task", "child", "task", "task", ""),
        NormTestKit.run(
            """
            class Task { String title }
            class Parent<T> {
              String remove(T item) { "task" }
              String remove(String title) { "parent" }
            }
            class Child extends Parent<Task> {
              String remove(Long id) { this.remove(Task(title: "task")) }
              String remove(String title) { "child" }
            }
            Void main() {
              var child = Child()
              printLine(child.remove(1))
              printLine(child.remove("title"))
              Function<String(Task)> bound = child.remove
              printLine(bound(Task(title: "task")))
              Function<String(Parent<Task>, Task)> unbound = Child.remove.function
              printLine(unbound(child, Task(title: "task")))
            }
            """));
  }

  @Test
  void invokesUnnamedFunctionParametersPositionallyIncludingCallbacks() {
    assertEquals(
        "task" + System.lineSeparator() + "saved" + System.lineSeparator(),
        NormTestKit.run(
            """
            value Item {
              Function<Void(String, Function<Void()>)> rename
              Void save() { rename("task", () { printLine("saved") }) }
            }
            Void main() {
              Item(rename: (String title, Function<Void()> saved) {
                printLine(title)
                saved()
              }).save()
            }
            """));
    assertFalse(
        NormTestKit.compile(
                """
                Void main() {
                  Function<Integer(Integer, Integer)> add = (Integer a, Integer b) { a + b }
                  add(argument0: 1, argument1: 2)
                }
                """)
            .isSuccess());
  }

  @Test
  void parsesCompleteTypesInExplicitLambdaParameters() {
    assertEquals(
        "task" + System.lineSeparator() + "saved" + System.lineSeparator(),
        NormTestKit.run(
            """
            Void each(Void visit(List<String> items)) { visit(["task"]) }
            Void save(Void action(String? title, Function<Void()> saved)) {
              action(title: null, saved: () { printLine("saved") })
            }
            Void main() {
              each((List<String> items) { printLine(items[0]) })
              save((String? title, Function<Void()> saved) { saved() })
            }
            """));
  }

  @Test
  void infersCollectionItemTypesBeforeContextualConstructorCallbacks() {
    assertEquals(
        "task" + System.lineSeparator() + "nested" + System.lineSeparator(),
        NormTestKit.run(
            """
            interface Widget { String text() }
            value Text implements Widget { String title String text() { title } }
            class Task { String title }
            value Each<T> implements Widget {
              List<T> items
              Function<Widget(T)> child
              Each(List<T> items, Widget child(T task)) { this.items = items this.child = child }
              String text() { child(items[0]).text() }
            }
            Void render(Widget widget) { printLine(widget.text()) }
            Void main() {
              render(Each(items: [Task(title: "task")]) { Text(task.title) })
              render(Each(items: [[Task(title: "nested")]]) { Text(task[0].title) })
            }
            """));
  }

  @Test
  void preservesFunctionRuntimeTypesThroughGenericCallbackWrappers() {
    assertEquals(
        "submitted" + System.lineSeparator(),
        NormTestKit.run(
            """
            enum Event { Submit(Function<Void(String)> action), Empty }
            class Scope {
              Function<Void(T)> bind<T>(Function<Void(T)> action) {
                (T value) { action(value) }
              }
            }
            value Input {
              Function<Void(String)> action
              Input(Void action(String title)) { this.action = action }
              Event event() { Event.Submit(Scope().bind<String>(action)) }
            }
            Void main() {
              var input = Input { printLine(title) }
              switch input.event() {
                case Submit(Function<Void(String)> action) { action("submitted") }
                case _ { require(condition: false, message: "callback runtime signature changed") }
              }
            }
            """));
  }

  @Test
  void invalidatesContextNamesWhenTheCallbackContractChanges() {
    try (var compiler = new CompilerSession()) {
      String initial =
          "Void send(Void callback(String title)) {} Void main() { send { printLine(title) } }";
      var before = compiler.compile(SourceFile.of(Path.of("context.norm"), initial));
      assertTrue(before.isSuccess(), () -> before.diagnostics().toString());
      var renamed =
          compiler.compile(
              SourceFile.of(
                  Path.of("context.norm"), initial.replace("String title", "String caption")));
      assertFalse(renamed.isSuccess());
      var updated =
          compiler.compile(
              SourceFile.of(Path.of("context.norm"), initial.replace("title", "caption")));
      assertTrue(updated.isSuccess(), () -> updated.diagnostics().toString());
    }
  }

  @Test
  void resolvesConstructorContractsAndOverloadsWithoutLeakingNames() {
    assertEquals(
        String.join(System.lineSeparator(), "submitted", "4", "outer", ""),
        NormTestKit.run(
            """
            value Input {
              private Function<Void(String)> action
              Input(Void action(String title)) { this.action = action }
              Void submit() { action("submitted") }
            }
            Void choose(String value, Void callback(String title)) { callback(value) }
            Void choose(Integer value, Void callback(Integer count)) { callback(value) }
            Void main() {
              String title = "outer"
              var input = Input { printLine(title) }
              input.submit()
              choose(4) { printLine(count) }
              printLine(title)
            }
            """));
  }

  @Test
  void bindsCallbackContractNamesForTrailingLambdas() {
    assertEquals(
        String.join(System.lineSeparator(), "todo", "42", "renamed", "nested", ""),
        NormTestKit.run(
            """
            Void submit(Void completed(String title)) { completed("todo") }
            Void each<T>(T item, Void visit(T value)) { visit(item) }
            Void pair(Void visit(String title, Integer count)) { visit(title: "nested", count: 1) }
            Void main() {
              submit { printLine(title) }
              each(42) { printLine(value) }
              submit { other in printLine("renamed") }
              pair {
                each(count) { printLine(title) }
              }
            }
            """));
  }

  @Test
  void keepsExplicitEmptyParametersAndUnnamedContractsUnambiguous() {
    assertFalse(
        NormTestKit.compile("Void send(Void callback(String title)) {} Void main() { send(() {}) }")
            .isSuccess());
    assertFalse(
        NormTestKit.compile(
                "Void send(Function<Void(String)> callback) {} Void main() { send {"
                    + " printLine(title) } }")
            .isSuccess());
  }
}
