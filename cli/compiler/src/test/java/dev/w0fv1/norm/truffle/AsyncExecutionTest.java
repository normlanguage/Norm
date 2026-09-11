package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class AsyncExecutionTest {
  @Test
  void rejectsWorkWithoutATaskScope() {
    assertEquals(
        "unscoped-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
        import std.concurrent.async
        import std.core.Exception
        Void main() {
          var rejected = false
          try { async { 1 } }
          catch Exception error { rejected = error.message == "async requires a task scope" }
          require(condition: rejected, message: "unowned work is rejected")
          printLine("unscoped-ok")
        }
        """));
  }

  @Test
  void executesTypedVoidAndFailedWorkWithoutUiDependencies() {
    assertEquals(
        "scoped-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
        import std.concurrent.async
        import std.concurrent.startTask
        import std.concurrent.Task
        import std.concurrent.TaskScope
        import std.context.withContext
        import std.core.Unit
        import std.core.Exception
        class WorkerScope implements TaskScope {
          Task<T> start<T>(Function<T()> work) { startTask<T>(work) }
        }
        class Store { Integer writes = 0 }
        Void main() {
          var store = Store()
          withContext<TaskScope>(value: WorkerScope(), action: () {
            require(condition: async { 21 }.then { result * 2 }.await() == 42, message: "typed result")
            Task<Unit> saved = async { store.writes = store.writes + 1 }
            require(condition: saved.await() == Unit.Value && store.writes == 1, message: "void result")
            var recovered = async<Integer> { throw Exception(message: "work failed") }
              .error { require(condition: failure.message == "work failed", message: "original failure") 7 }
            require(condition: recovered.await() == 7, message: "failure recovery")
          })
          printLine("scoped-ok")
        }
        """));
  }

  @Test
  void selectsTheInnermostScopeAndRestoresTheCaller() {
    assertEquals(
        "context-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
        import std.concurrent.async
        import std.concurrent.startTask
        import std.concurrent.Task
        import std.concurrent.TaskScope
        import std.context.withContext
        import std.context.currentContext
        class WorkerScope implements TaskScope {
          Integer starts = 0
          Task<T> start<T>(Function<T()> work) {
            starts = starts + 1
            startTask<T>(work)
          }
        }
        Void main() {
          var outer = WorkerScope()
          var inner = WorkerScope()
          withContext<TaskScope>(value: outer, action: () {
            async { 1 }.await()
            withContext<TaskScope>(value: inner, action: () { async { 2 }.await() return })
            require(condition: currentContext<TaskScope>() == outer, message: "outer context restored")
            async { 3 }.await()
            return
          })
          require(condition: outer.starts == 2 && inner.starts == 1, message: "nearest scope selected")
          require(condition: currentContext<TaskScope>() == null, message: "caller context restored")
          printLine("context-ok")
        }
        """));
  }
}
