package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class FieldHandleExecutionTest {
  @Test
  void preservesNestedMapChangesWhenASubscriberFails() {
    assertEquals(
        "map-subscriber-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            import std.core.Exception
            class Model { Map<String, List<Integer>> values = Map<String, List<Integer>>() }
            class Changes { List<List<Integer>> values = [] }
            Void main() {
              var model = Model()
              var changes = Changes()
              model.values.put(key: "a", value: [1])
              var first = model.values.onChange { throw Exception(message: "subscriber failed") }
              var second = model.values.onChange { changes.values.add(newValue["a"]) }
              var caught = false
              try { model.values["a"].add(2) }
              catch Exception failure { caught = failure.message == "subscriber failed" }
              require(condition: caught && model.values["a"] == [1, 2], message: "mutation is committed before callback failure")
              require(condition: changes.values == [[1, 2]], message: "remaining subscriber sees the nested map change")
              first.close()
              second.close()
              printLine("map-subscriber-ok")
            }
            """));
  }

  @Test
  void observesNestedChangesAndRebindsEqualFieldReplacements() {
    assertEquals(
        "nested-changes-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            class Model { List<List<Integer>> items = [[1]] }
            class Changes { List<List<List<Integer>>> after = [] }
            Void edit(List<Integer> values) { values.add(7) }
            List<Integer> read(Model model) { model.items[0] }
            Void main() {
              var model = Model()
              var changes = Changes()
              var first = model.items.onChange {
                if newValue == [[1, 2]] { model.items[0].add(3) }
              }
              var second = model.items.onChange { changes.after.add(newValue) }
              var detached = model.items[0]
              detached.add(8)
              edit(model.items[0])
              var returned = read(model)
              returned.add(9)
              require(condition: model.items == [[1]], message: "indexed values copy at assignment argument and return boundaries")
              model.items = [[1]]
              require(condition: changes.after.size() == 0, message: "equal replacement is silent")
              model.items[0].add(2)
              require(condition: changes.after == [[[1, 2, 3]], [[1, 2]]], message: "nested reentrant notifications preserve snapshots")
              model.items[0] = [4]
              model.items[0].add(5)
              require(condition: changes.after[3] == [[4, 5]], message: "replaced child becomes observed")
              first.close()
              second.close()
              model.items[0].add(6)
              require(condition: changes.after.size() == 4, message: "closing detaches nested subscriptions")
              printLine("nested-changes-ok")
            }
            """));
  }

  @Test
  void observesListAndMapMutationsWithIndependentSnapshots() {
    assertEquals(
        "collection-changes-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            class Model {
              List<String> items = []
              Map<String, Integer> index = Map<String, Integer>()
            }
            class Changes {
              List<List<String>> before = []
              List<List<String>> after = []
              List<Map<String, Integer>> maps = []
            }
            Void main() {
              var model = Model()
              var changes = Changes()
              var items = model.items.onChange {
                changes.before.add(oldValue)
                changes.after.add(newValue)
              }
              var index = model.index.onChange { changes.maps.add(newValue) }
              model.items.add("a")
              model.items[0] = "b"
              model.items.removeAt(0)
              require(condition: changes.before == [[], ["a"], ["b"]], message: "list old snapshots")
              require(condition: changes.after == [["a"], ["b"], []], message: "list new snapshots")
              model.index.put(key: "a", value: 1)
              model.index.put(key: "a", value: 1)
              model.index["a"] = 2
              model.index.remove("a")
              require(condition: changes.maps.size() == 3, message: "map changes exclude equal replacement")
              require(condition: changes.maps[0]["a"] == 1 && changes.maps[1]["a"] == 2 && changes.maps[2].size() == 0, message: "map snapshots remain independent")
              var detached = model.items
              detached.add("detached")
              require(condition: changes.after.size() == 3, message: "copied list has independent storage")
              items.close()
              index.close()
              model.items.add("closed")
              model.index.put(key: "closed", value: 3)
              require(condition: changes.after.size() == 3 && changes.maps.size() == 3, message: "closed collection subscriptions detach")
              printLine("collection-changes-ok")
            }
            """));
  }

  @Test
  void releasesExplicitlyClosedSubscriptionsFromTheirResourceOwner() {
    assertEquals(
        "ownership-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.io.Resource
            import std.io.ResourceOwner
            import std.context.withContext
            import std.observation.onChange
            class Owner implements ResourceOwner {
              List<Resource> resources = []
              Void own(Resource resource) { resources.add(resource) }
              Void release(Resource resource) { resources = [for (owned : resources) if (owned != resource) owned] }
              Void execute(Function<Void()> action) { withContext<ResourceOwner>(value: this, action: action) }
            }
            class Model { String title = "before" }
            Void main() {
              var owner = Owner()
              var model = Model()
              var subscription = withContext<ResourceOwner, Resource>(value: owner, action: () { model.title.onChange {} })
              require(condition: owner.resources.size() == 1, message: "subscription is automatically owned")
              subscription.close()
              subscription.close()
              require(condition: owner.resources.size() == 0, message: "early close removes the subscription from its owner")
              printLine("ownership-ok")
            }
            """));
  }

  @Test
  void notifiesOtherFieldObserversBeforePropagatingSubscriberFailure() {
    assertEquals(
        "observer-failure-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            import std.core.Exception
            class Model { String title = "before" }
            class Seen { String title = "" }
            Void main() {
              var model = Model()
              var seen = Seen()
              var failed = model.title.onChange { throw Exception(message: "subscriber failed") }
              var retained = model.title.onChange { seen.title = newValue }
              var caught = false
              try { model.title = "after" }
              catch Exception failure { caught = failure.message == "subscriber failed" }
              require(condition: caught && seen.title == "after" && model.title == "after", message: "failed side effect must not hide a committed write from other observers")
              failed.close()
              retained.close()
              printLine("observer-failure-ok")
            }
            """));
  }

  @Test
  void preservesNullableChangeValuesDuringReentrantWritesAndObservesListReplacement() {
    assertEquals(
        "change-values-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            class Model { String? title = null List<String> items = [] }
            class Changes { List<String> values = [] }
            Void main() {
              var model = Model()
              var changes = Changes()
              var first = model.title.onChange {
                if newValue == "first" { model.title = "second" }
              }
              var second = model.title.onChange {
                changes.values.add((oldValue ?? "null") + ":" + (newValue ?? "null"))
              }
              var items = model.items.onChange {
                require(condition: oldValue.size() == 0 && newValue == ["task"], message: "list field replacement")
                changes.values.add("items")
              }
              model.title = "first"
              model.title = null
              model.items = ["task"]
              require(condition: changes.values == ["first:second", "null:first", "second:null", "items"], message: "notifications retain each write's actual values")
              first.close()
              second.close()
              items.close()
              printLine("change-values-ok")
            }
            """));
  }

  @Test
  void observesFieldAssignmentsThroughTypedExtensionAndReleasesSubscription() {
    assertEquals(
        "field-change-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.onChange
            class Model { String title = "first" Integer count = 0 Boolean active = false }
            class Changes { List<String> values = [] }
            Void main() {
              var model = Model()
              var changes = Changes()
              var title = model.title.onChange { changes.values.add(oldValue + ":" + newValue) }
              var count = model.count.onChange { changes.values.add("count") }
              var active = model.active.onChange { changes.values.add("active") }
              model.title = "second"
              model.title = "second"
              model.count = 1
              model.active = true
              require(condition: changes.values == ["first:second", "count", "active"], message: "typed independent field changes")
              title.close()
              title.close()
              model.title = "third"
              require(condition: changes.values.size() == 3, message: "unsubscribe is idempotent")
              count.close()
              active.close()
              printLine("field-change-ok")
            }
            """));
  }

  @Test
  void rejectsNonStorageAndInaccessibleOrIncompatibleCaptures() {
    for (String source :
        java.util.List.of(
            "Void take(FieldHandle<String> field) {} Void main() { String title = \"local\""
                + " take(title) }",
            "value Model { String title } Void take(FieldHandle<String> field) {} Void main() {"
                + " take(Model(\"value\").title) }",
            "class Model { private String title = \"private\" } Void take(FieldHandle<String>"
                + " field) {} Void main() { take(Model().title) }",
            "class Model { String title { get { \"computed\" } } } Void take(FieldHandle<String>"
                + " field) {} Void main() { take(Model().title) }",
            "class Model { String? title = null } Void take(FieldHandle<String> field) {} Void"
                + " main() { var model = Model() if model.title != null { take(model.title) } }")) {
      assertFalse(NormTestKit.compile(source).isSuccess(), source);
    }
  }

  @Test
  void capturesFieldsContextuallyAndEvaluatesTheReceiverOnce() {
    assertEquals(
        "captured" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Model {
              private String title = "before"
              FieldHandle<String> handle() { title }
            }
            class Box<T> { T content }
            class Factory {
              Integer calls = 0
              Box<String> box = Box<String>("before")
              Box<String> get() { calls = calls + 1 box }
            }
            FieldHandle<T> capture<T>(FieldHandle<T> field) { field }
            Void main() {
              var factory = Factory()
              var handle = capture(factory.get().content)
              handle.write("after")
              require(condition: factory.calls == 1 && factory.box.content == "after", message: "single receiver evaluation")
              var privateField = Model().handle()
              privateField.write("private")
              require(condition: privateField.read() == "private", message: "lexically accessible field")
              var preserved = capture(handle)
              require(condition: preserved == handle, message: "existing handle")
              printLine("captured")
            }
            """));
  }

  @Test
  void retainsAFieldAcrossReturnsAndClosuresAndUsesNormalWrites() {
    assertEquals(
        "renamed" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.observeFields
            class Model { String title = "first" }
            class Changes { Integer count = 0 }
            FieldHandle<String> create() { Model.title.field.bind(Model()) }
            Void main() {
              var handle = create()
              Function<Void(String)> update = (String title) { handle.write(title) }
              update("renamed")
              printLine(handle.read())
              var model = Model()
              var changes = Changes()
              var subscription = observeFields(target: model, read: (Integer field) {},
                changed: (Integer field) { changes.count = changes.count + 1 })
              var first = Model.title.field.bind(model)
              var second = Model.title.field.bind(model)
              require(condition: first == second && first != handle, message: "field location identity")
              first.write("next")
              second.write("next")
              require(condition: model.title == "next" && changes.count == 1, message: "field notifications")
              model.title = "direct"
              require(condition: first.read() == "direct", message: "live field storage")
              subscription.close()
            }
            """));
    assertFalse(
        NormTestKit.compile(
                """
                class Model { String title = "first" }
                Void main() { Model.title.field.bind(Model()).write(1) }
                """)
            .isSuccess());
  }
}
