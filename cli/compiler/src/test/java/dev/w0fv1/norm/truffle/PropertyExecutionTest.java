package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class PropertyExecutionTest {
  @Test
  void constructsImmutableLayoutValuesThroughOverloads() throws Exception {
    assertEquals(
        "2:0:1:12" + System.lineSeparator(),
        NormTestKit.run(
            """
            value Row<T> {
              List<T> children
              Integer spacing
              Row(List<T> children) { this.children = children spacing = 0 }
              Row(List<T> children, Integer spacing) { this.children = children this.spacing = spacing }
            }
            Void main() {
              var first = Row<Integer>([1, 2])
              var second = Row<Integer>(children: [3], spacing: 12)
              printLine("${first.children.size()}:${first.spacing}:${second.children.size()}:${second.spacing}")
            }
            """));
  }

  @Test
  void initializesPrivateStateForEachInstanceAlongsidePublicInputs() throws Exception {
    assertEquals(
        "Todo:1:0" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Page {
              private Integer count = 0
              String title
              Void increment() { count = count + 1 }
              Integer current() { count }
            }
            Void main() {
              var first = Page(title: "Todo")
              var second = Page(title: "Other")
              first.increment()
              printLine("${first.title}:${first.current()}:${second.current()}")
            }
            """));
  }

  @Test
  void reflectsPropertyAccessorsWithoutInventingStorageFields() throws Exception {
    assertEquals(
        "1" + System.lineSeparator() + "1" + System.lineSeparator() + "1" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Counter {
              private Integer stored = 0
              Integer value { get { return stored } set(next) { stored = next } }
            }
            Void main() {
              printLine(Counter.class.fields().size())
              Integer getters = 0
              Integer setters = 0
              for function : Counter.class.functions() {
                if function.name() == "value" {
                  if function.parameters().size() == 1 { getters = getters + 1 }
                  if function.parameters().size() == 2 { setters = setters + 1 }
                }
              }
              printLine(getters)
              printLine(setters)
            }
            """));
  }

  @Test
  void dispatchesOverriddenSettersIndependentlyOfTheirLocalParameterName() throws Exception {
    assertEquals(
        "6" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Base {
              private Integer stored = 0
              Integer value { get { return stored } set(next) { stored = next } }
            }
            class Child extends Base {
              private Integer storedChild = 0
              Child() { super() }
              Integer value { get { return storedChild } set(replacement) { storedChild = replacement * 2 } }
            }
            Void main() { Base item = Child() item.value = 3 printLine(item.value) }
            """));
  }

  @Test
  void capturesInheritedPropertiesAndInvokesImplicitFunctionProperties() throws Exception {
    assertEquals(
        "6" + System.lineSeparator() + "10" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Base {
              private Integer stored = 3
              Integer value { get { return stored } set(next) { stored = next } }
            }
            class Child extends Base {
              Child() { super() }
              Function<Integer(Integer)> transform {
                get { return (Integer input) { input + value } }
              }
              Function<Integer()> reader() { return () { value } }
              Void change(Integer next) { value = next }
              Integer apply(Integer input) { return transform(input) }
            }
            Void main() {
              var child = Child()
              var read = child.reader()
              child.change(6)
              printLine(read())
              printLine(child.apply(4))
            }
            """));
  }

  @Test
  void readsAndWritesImplicitReceiverPropertiesAndHonorsLocalShadowing() throws Exception {
    assertEquals(
        "5" + System.lineSeparator() + "12" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Counter {
              private Integer stored = 2
              Integer value { get { return stored } set(next) { stored = next } }
              Void increment() { value = value + 3 }
              Integer read() { return value }
              Integer shadow(Integer value) { return value + this.value }
            }
            Void main() {
              var counter = Counter()
              counter.increment()
              printLine(counter.read())
              printLine(counter.shadow(7))
            }
            """));
  }

  @Test
  void invokesAFunctionReturnedByAProperty() throws Exception {
    assertEquals(
        "5" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Actions {
              Function<Integer(Integer)> transform {
                get { return (Integer value) { value + 1 } }
              }
            }
            Void main() { printLine(Actions().transform(4)) }
            """));
  }

  @Test
  void preservesGenericTypesAndVirtualGetterDispatch() throws Exception {
    assertEquals(
        "hello" + System.lineSeparator() + "9" + System.lineSeparator(),
        NormTestKit.run(
            """
            class Box<T> {
              private T stored
              Box(T initial) { stored = initial }
              T value { get { return stored } set(next) { stored = next } }
            }
            class Base { Integer value { get { return 1 } } }
            class Child extends Base {
              Child() { super() }
              Integer value { get { return 9 } }
            }
            Void main() {
              var box = Box<String>("before")
              box.value = "hello"
              printLine(box.value)
              Base child = Child()
              printLine(child.value)
            }
            """));
  }

  @Test
  void routesReadsAndWritesThroughAccessorsWithOneReceiverEvaluation() throws Exception {
    var result =
        NormTestKit.run(
            """
            class Counter {
              private Integer stored
              Integer calls
              Counter() { stored = 1 calls = 0 }
              Integer value {
                get { return stored * 2 }
                set(next) { stored = next }
              }
              Counter receiver() { calls = calls + 1 return this }
            }
            Void main() {
              var counter = Counter()
              counter.receiver().value = 6
              printLine(counter.value)
              printLine(counter.calls)
            }
            """);
    assertEquals("12" + System.lineSeparator() + "1" + System.lineSeparator(), result);
  }
}
