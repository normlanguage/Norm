package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class NullablePatternExecutionTest {
  @Test
  void nullableSubtypeCoversNullButPreservesTheOtherTypes() {
    assertEquals(
        "null\nchild\nparent\n",
        NormTestKit.run(
                """
                class Parent {}
                class Child extends Parent {}
                Void show(Parent? source) {
                  switch source {
                    case Child? child { printLine(if child == null { "null" } else { "child" }) }
                    case Parent parent { printLine("parent") }
                  }
                }
                Void main() { show(null); show(Child()); show(Parent()) }
                """)
            .replace("\r\n", "\n"));
  }

  @Test
  void rejectsNullBranchAlreadyCoveredByNullableBinding() {
    assertFalse(
        NormTestKit.compile(
                """
                Void show(String? source) {
                  switch source {
                    case String? text { printLine(text) }
                    case null { printLine("unreachable") }
                  }
                }
                Void main() {}
                """)
            .isSuccess());
  }

  @Test
  void bindsNullOnlyWhenThePatternTypeIsNullable() {
    assertEquals(
        String.join(System.lineSeparator(), "null", "text", "fallback", ""),
        NormTestKit.run(
            """
            enum Input { Text(String? text) }
            Void show(Input input) {
              switch input {
                case Text(String? text) { printLine(text) }
              }
            }
            Void main() {
              show(Input.Text(null))
              show(Input.Text("text"))
              String? missing = null
              switch missing {
                case String text { printLine(text) }
                case null { printLine("fallback") }
              }
            }
            """));
  }
}
