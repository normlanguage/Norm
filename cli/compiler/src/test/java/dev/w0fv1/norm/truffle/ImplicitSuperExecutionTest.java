package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ImplicitSuperExecutionTest {
  @Test
  void initializesInheritedFieldsBeforePrivateStateAndPreservesGenericParents() throws Exception {
    assertEquals(
        String.join(System.lineSeparator(), "parent", "child", "parent", "child", "super-ok", ""),
        NormTestKit.run(
            """
            Integer initializeChild() { printLine("child") 42 }
            class Base<T> {
              Integer number
              T? item = null
              Base(Integer number = 41) { this.number = number printLine("parent") }
            }
            class Child<T> extends Base<T> {
              private Integer next = initializeChild()
              String title
              Integer read() { next }
            }
            class Leaf extends Child<String> { Leaf() { super(title: "todo") } }
            Void main() {
              var child = Child<String>(title: "first")
              require(condition: child.number == 41 && child.read() == 42, message: "parent precedes private initializer")
              child.item = "typed"
              require(condition: child.item == "typed", message: "generic parent retains its type")
              var leaf = Leaf()
              require(condition: leaf.title == "todo" && leaf.read() == 42, message: "explicit and implicit super calls compose")
              printLine("super-ok")
            }
            """));
  }
}
