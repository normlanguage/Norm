package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class NonNullAssertionExecutionTest {
  @Test
  void reportsNullAtTheAssertionWithoutExplicitExceptionImports() {
    var failure =
        assertThrows(
            NormExecutionException.class,
            () -> NormTestKit.run("Void main() { String? value = null printLine(value!!) }"));
    assertEquals("non-null assertion failed", failure.getMessage());
    assertEquals(1, failure.line());
  }

  @Test
  void checksOnceAndReturnsTheNonNullableValue() throws Exception {
    assertEquals(
        "assert-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.core.Exception
            class Todo { Long? id = null }
            class Reads {
              Integer count = 0
              Todo? read() { count = count + 1 Todo(id: 42) }
            }
            T present<T>(T? value) { value!! }
            Void main() {
              var reads = Reads()
              Long id = reads.read()!!.id!!
              require(condition: id == 42 && reads.count == 1, message: "evaluate once with postfix precedence")
              require(condition: present<String>("todo") == "todo", message: "generic assertion")
              Boolean? checked = false
              require(condition: !checked!!, message: "prefix negation follows assertion")
              var rejected = false
              try { Long missing = Todo().id!! }
              catch Exception error { rejected = error.message == "non-null assertion failed" }
              require(condition: rejected, message: "null assertion throws a catchable exception")
              printLine("assert-ok")
            }
            """));
  }
}
