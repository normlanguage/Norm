package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ObjectReflectionExecutionTest {
  @Test
  void writesReflectedFieldsThroughInterceptorsAndChecksTypes() {
    assertEquals(
        "written" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.core.Exception
            import std.observation.observeFields
            import std.annotation.FieldInterceptor
            import std.annotation.RuntimeRetention
            annotation Increment implements FieldInterceptor<Integer>, RuntimeRetention {
              Integer before(FieldContext context, Integer value) { value + 1 }
            }
            class Counter { @Increment() Integer number = 0 }
            class Events { Integer writes = 0 }
            value Snapshot { Integer number }
            Void main() {
              var counter = Counter()
              var events = Events()
              var subscription = observeFields(target: counter,
                read: (Integer field) {}, changed: (Integer field) { events.writes = events.writes + 1 })
              Counter.number.field.write(receiver: counter, value: 7)
              require(condition: counter.number == 8 && events.writes == 1, message: "normal field write")
              var rejected = false
              try { Snapshot.number.field.write(receiver: Snapshot(1), value: 2) }
              catch Exception failure { rejected = true }
              require(condition: rejected, message: "immutable target")
              subscription.close()
              printLine("written")
            }
            """));
    assertFalse(
        NormTestKit.compile(
                """
                class Counter { Integer number }
                Void main() { Counter.number.field.write(receiver: Counter(1), value: "wrong") }
                """)
            .isSuccess());
  }

  @Test
  void runsFieldInterceptorsWhenCopyingConfiguration() {
    assertEquals(
        "intercepted" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.annotation.FieldInterceptor
            import std.annotation.RuntimeRetention
            annotation Increment implements FieldInterceptor<Integer>, RuntimeRetention {
              Integer before(FieldContext context, Integer value) { value + 1 }
            }
            class Counter { @Increment() Integer number }
            Void main() {
              var target = Counter(number: 0)
              var source = Counter(number: 8)
              Counter.number.field.copy(target: target, source: source)
              require(condition: target.number == source.number + 1, message: "reflection uses field interceptors")
              printLine("intercepted")
            }
            """));
  }

  @Test
  void rejectsCopyingIntoValuesAndAcrossRuntimeOwners() {
    assertEquals(
        "guarded" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.core.Exception
            value Snapshot { Integer number }
            interface Item {}
            class First implements Item { Integer number }
            class Second implements Item { Integer number }
            Void main() {
              require(condition: classOf(Snapshot(number: 1)).isValue(), message: "value category")
              var rejected = false
              try { Snapshot.number.field.copy(target: Snapshot(number: 1), source: Snapshot(number: 2)) }
              catch Exception failure { rejected = true }
              require(condition: rejected, message: "value cannot be mutated through reflection")
              Item first = First(number: 1)
              Item second = Second(number: 2)
              rejected = false
              try {
                for field : classOf(first).fields() { field.copy(target: second, source: first) }
              } catch Exception failure { rejected = true }
              require(condition: rejected, message: "runtime owner is checked")
              printLine("guarded")
            }
            """));
  }

  @Test
  void copiesGenericFieldsAndNotifiesSubscribers() {
    assertEquals(
        "copied" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.observation.observeFields
            class Box<T> { T content }
            class Events { Integer writes = 0 }
            Void main() {
              var target = Box<List<Integer>>(content: [1])
              var source = Box<List<Integer>>(content: [2, 3])
              var events = Events()
              var subscription = observeFields(target: target,
                read: (Integer field) {},
                changed: (Integer field) { events.writes = events.writes + 1 })
              for field : classOf(target).fields() { field.copy(target: target, source: source) }
              require(condition: target.content == [2, 3] && events.writes == 1, message: "typed field copy notifies")
              for field : classOf(target).fields() { field.copy(target: target, source: source) }
              require(condition: events.writes == 1, message: "equal values do not notify")
              subscription.close()
              printLine("copied")
            }
            """));
  }

  @Test
  void copiesPublicFieldsThroughTheRuntimeTypeAndRetainsPrivateState() {
    assertEquals(
        "new:7" + System.lineSeparator(),
        NormTestKit.run(
            """
            interface Item { String show() }
            class Parent { String title }
            class Row extends Parent implements Item {
              private Integer count = 0
              Row(String title) { super(title) }
              Void edit() { count = 7 }
              String show() { "${title}:${count}" }
            }
            Void main() {
              var row = Row(title: "old")
              row.edit()
              Item target = row
              Item source = Row(title: "new")
              require(condition: classOf(target) == classOf(source), message: "runtime class identity")
              var actual = classOf(target)
              Class<?> dynamic = actual
              Class<?> literal = Row.class
              require(condition: dynamic == literal, message: "static views do not change class identity")
              Map<Class<?>, String> names = Map<>()
              names.put(key: dynamic, value: "row")
              require(condition: names.containsKey(literal), message: "class hashing agrees with equality")
              require(condition: !classOf(target).isValue(), message: "class category")
              for field : classOf(target).fields() {
                if field.isPublic() { field.copy(target: target, source: source) }
              }
              printLine(target.show())
            }
            """));
  }
}
