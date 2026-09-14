package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class DefaultArgumentExecutionTest {
  @Test
  void evaluatesReferenceDefaultsToLiveObjectFields() {
    assertOutput(
        """
        class Box {
          Integer value = 1
          Integer update(ref<Integer> target = &this.value) { *target = 7; value }
        }
        Void main() { printLine(Box().update()) }
        """,
        "7");
  }

  @Test
  void specializesDefaultsForConstructorsEnumsAndInheritedInterfaces() {
    assertOutput(
        """
        class Box<T> {
          List<T> items
          Box(List<T> items = List<T>()) { this.items = items }
        }
        class Parent<T> {
          List<T> items
          Parent(List<T> items = List<T>()) { this.items = items }
        }
        class Child<T> extends Parent<T> { Child() { super() } }
        enum Result<T> { Items(List<T> items = List<T>()) }
        interface Reading<T> {
          List<T> read(List<T> items = List<T>())
        }
        interface Names extends Reading<String> {}
        class Reader implements Names {
          List<String> read(List<String> items) { items }
        }
        Void main() {
          var box = Box<Integer>()
          box.items.add(7)
          printLine(box.items[0])
          var child = Child<String>()
          child.items.add("child")
          printLine(child.items[0])
          var result = Result<Integer>.Items()
          switch result { case Items(List<Integer> items) { items.add(9); printLine(items[0]) } }
          Names reading = Reader()
          var names = reading.read()
          names.add("Norm")
          printLine(names[0])
        }
        """,
        "7",
        "child",
        "9",
        "Norm");
  }

  @Test
  void evaluatesExplicitArgumentsBeforeDefaultsAndAllocatesEachDefaultPerCall() {
    assertOutput(
        """
        Integer logged(String name, Integer value) { printLine(name); value }
        Integer sum(Integer first = logged(name: "first", value: 1), Integer second = logged(name: "second", value: 2)) {
          first + second
        }
        List<Integer> values(List<Integer> items = List<Integer>()) { items }
        Void main() {
          printLine(sum(second: logged(name: "explicit", value: 9)))
          printLine(sum())
          var first = values()
          first.add(7)
          var second = values()
          second.add(9)
          printLine(first[0])
          printLine(second[0])
        }
        """,
        "explicit",
        "first",
        "10",
        "first",
        "second",
        "3",
        "7",
        "9");
  }

  @Test
  void evaluatesReceiverAndDefaultsOnceInDeclarationContext() {
    assertOutput(
        """
        class Counter {
          Integer count = 0
          Integer next() { count = count + 1; count }
          Integer read(Integer number = this.next()) { number }
        }
        Counter receiver(Counter counter) { printLine("receiver"); counter }
        Void main() {
          var counter = Counter()
          printLine(receiver(counter).read())
          printLine(receiver(counter).read(9))
          printLine(counter.count)
          Counter? absent = null
          printLine(absent?.read() == null)
          printLine(counter.count)
        }
        """,
        "receiver",
        "1",
        "receiver",
        "9",
        "1",
        "true",
        "1");
  }

  @Test
  void specializesGenericDefaultsAndClosuresInTheirOwnTypeContext() {
    assertOutput(
        """
        List<T> values<T>(List<T> items = List<T>()) { items }
        Function<List<T>()> factory<T>(Function<List<T>()> create = () { List<T>() }) { create }
        class Box<T> {
          T value
          T read(T fallback = value) { fallback }
        }
        Void main() {
          var numbers = values<Integer>()
          numbers.add(7)
          printLine(numbers[0])
          var create = factory<String>()
          var names = create()
          names.add("Norm")
          printLine(names[0])
          printLine(Box(value: 42).read())
        }
        """,
        "7",
        "Norm",
        "42");
  }

  @Test
  void usesDefaultsFromTheSelectedInterfaceDeclaration() {
    assertOutput(
        """
        interface Reading {
          Integer base()
          Integer read(Integer number = this.base())
        }
        class Counter implements Reading {
          Integer base() { 7 }
          Integer read(Integer number = 9) { number }
        }
        Void main() {
          var counter = Counter()
          Reading reading = counter
          printLine(reading.read())
          printLine(counter.read())
        }
        """,
        "7",
        "9");
  }
}
