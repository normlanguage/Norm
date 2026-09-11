package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class TypePatternTest {
  @Test
  void matchesCompleteReifiedGenericIdentity() {
    assertEquals(
        "true\nfalse\n",
        NormTestKit.run(
                """
                class Box<T> { T value }
                Boolean matches<T>(Any source) {
                  return switch source {
                    case Box<T> box { break true }
                    case _ { break false }
                  }
                }
                Void main() {
                  Any box = Box<Integer>(value: 7)
                  printLine(matches<Integer>(box))
                  printLine(matches<String>(box))
                }
                """)
            .replace("\r\n", "\n"));
  }

  @Test
  void narrowsNominalSubtypesAndRejectsUnrelatedValues() {
    assertEquals(
        "child\nother\n",
        NormTestKit.run(
                """
                class Parent { Parent() {} }
                class Child extends Parent { Child() { super() } }
                String describe(Any source) {
                  return switch source {
                    case Parent parent { break "child" }
                    case _ { break "other" }
                  }
                }
                Void main() {
                  printLine(describe(Child()))
                  printLine(describe("hello"))
                }
                """)
            .replace("\r\n", "\n"));
  }

  @Test
  void requiresFallbackForOpenTypeTests() {
    assertFalse(
        NormTestKit.compile(
                """
                Integer select(Any value) {
                  return switch value { case Integer number { break number } }
                }
                Void main() {}
                """)
            .isSuccess());
  }

  @Test
  void rejectsSubtypeBranchCoveredByParent() {
    assertFalse(
        NormTestKit.compile(
                """
                class Parent { Parent() {} }
                class Child extends Parent { Child() { super() } }
                Integer select(Any value) {
                  return switch value {
                    case Parent parent { break 1 }
                    case Child child { break 2 }
                    case _ { break 3 }
                  }
                }
                Void main() {}
                """)
            .isSuccess());
  }

  @Test
  void keepsDistinctGenericBranchesReachable() {
    assertEquals(
        "2\n",
        NormTestKit.run(
                """
                class Box<T> { T value }
                Integer select(Any value) {
                  return switch value {
                    case Box<String> text { break 1 }
                    case Box<Integer> number { break number.value }
                    case _ { break 3 }
                  }
                }
                Void main() { printLine(select(Box<Integer>(value: 2))) }
                """)
            .replace("\r\n", "\n"));
  }
}
