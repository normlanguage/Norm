package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ThrowExpressionExecutionTest {
  @Test
  void coalescesOnceAndThrowsOnlyForMissingValues() {
    assertEquals(
        "throw-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.core.Exception
            class Todo { Long id }
            class Repository {
              Integer reads = 0
              Integer failures = 0
              Todo? find(Boolean found) { reads = reads + 1 if found { Todo(42) } else { null } }
              Exception missing() { failures = failures + 1 Exception(message: "missing") }
            }
            T present<T>(T? value) { value ?? throw Exception(message: "missing") }
            Void main() {
              var repository = Repository()
              var todo = repository.find(true) ?? throw repository.missing()
              Long id = todo.id
              require(condition: id == 42 && repository.reads == 1 && repository.failures == 0, message: "short circuit")
              var caught = false
              var finalized = false
              try { var missing = repository.find(false) ?? throw repository.missing() }
              catch Exception failure { caught = failure.message == "missing" }
              finally { finalized = true }
              require(condition: caught && finalized && repository.reads == 2 && repository.failures == 1, message: "exception flow")
              require(condition: present<Long>(42) == 42, message: "generic result")
              printLine("throw-ok")
            }
            """));
  }

  @Test
  void rejectsNonExceptionOperands() {
    assertFalse(
        NormTestKit.compile("Void main() { Long? id = null Long value = id ?? throw 42 }")
            .isSuccess());
  }
}
