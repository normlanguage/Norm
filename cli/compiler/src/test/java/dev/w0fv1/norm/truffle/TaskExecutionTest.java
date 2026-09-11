package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class TaskExecutionTest {
  @Test
  void reportsOnlyUnobservedFailuresOnTheDeliveryQueue() {
    assertEquals(
        "unhandled-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.concurrent.TaskExecutor
            import std.core.Exception
            class ReportingQueue implements TaskExecutor {
              List<Function<Void()>> pending = []
              List<Exception> failures = []
              Void dispatch(Function<Void()> action) { pending.add(action) }
              Void report(Exception error) { failures.add(error) }
              Void drain() {
                for pending.size() != 0 {
                  var ready = pending
                  pending = []
                  for action : ready { action() }
                }
              }
            }
            Void main() {
              var queue = ReportingQueue()
              var original = Exception(message: "unhandled work")
              var lost = startTask<Integer>(executor: queue) { throw original }
              lost.termination()!!.await()
              require(condition: queue.failures.size() == 0, message: "report waits for delivery queue")
              queue.drain()
              require(condition: queue.failures == [original], message: "original failure reported once")
              var handled = startTask<Integer>(executor: queue) { throw Exception(message: "handled") }
              var recovered = handled.error { 42 }
              handled.termination()!!.await()
              queue.drain()
              require(condition: recovered.await() == 42 && queue.failures.size() == 1, message: "handler consumes parent failure")
              var parent = startTask(executor: queue) { 1 }
              parent.then<Integer> { throw Exception(message: "callback failure") }
              parent.termination()!!.await()
              queue.drain()
              require(condition: queue.failures.size() == 2 && queue.failures[1].message == "callback failure", message: "unhandled leaf failure reported")
              var closed = startTask<Integer>(executor: queue) { throw Exception(message: "closed") }
              closed.termination()!!.await()
              closed.close()
              queue.drain()
              require(condition: queue.failures.size() == 2, message: "explicitly closed task is observed")
              printLine("unhandled-ok")
            }
            """));
  }

  @Test
  void exposesAnIndependentTerminationBarrierForOwnedWork() {
    assertEquals(
        "termination-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.core.Unit
            Void main() {
              var work = startTask { 42 }
              var terminated = work.termination()!!
              require(condition: terminated.await() == Unit.Value, message: "execution cleanup completed")
              require(condition: work.await() == 42, message: "result remains readable")
              require(condition: terminated.termination() == null, message: "barrier is not another owned worker")
              terminated.close()
              work.close()
              printLine("termination-ok")
            }
            """));
  }

  @Test
  void releasesTaskOwnershipBeforeClosingTheGuestRuntime() {
    assertEquals(
        "main" + System.lineSeparator() + "released" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.context.withContext
            import std.io.Resource
            import std.io.ResourceOwner
            class ExitOwner implements ResourceOwner {
              Resource? pending = null
              Void own(Resource resource) { pending = resource }
              Void release(Resource resource) { pending = null printLine("released") }
              Void execute(Function<Void()> action) { action() }
            }
            Void main() {
              withContext<ResourceOwner>(value: ExitOwner(), action: () {
                startTask { 42 }
                return
              })
              printLine("main")
            }
            """));
  }

  @Test
  void ownsEveryStageAndReleasesFinishedOrCancelledTasks() {
    assertEquals(
        "task-owner-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.concurrent.Task
            import std.concurrent.TaskExecutor
            import std.io.Resource
            import std.io.ResourceOwner
            import std.context.withContext
            import std.core.Exception
            class Owner implements ResourceOwner {
              List<Resource> resources = []
              Integer registrations = 0
              Integer releases = 0
              Boolean closed = false
              Void own(Resource resource) {
                if closed { resource.close() throw Exception(message: "owner closed") }
                registrations = registrations + 1
                resources.add(resource)
              }
              Void release(Resource resource) {
                resources = [for (owned : resources) if (owned != resource) owned]
                releases = releases + 1
              }
              Void execute(Function<Void()> action) { action() }
              Void close() {
                closed = true
                var remaining = resources
                resources = []
                for resource : remaining { resource.close() }
              }
            }
            class DeliveryQueue implements TaskExecutor {
              List<Function<Void()>> pending = []
              Integer delivered = 0
              Void dispatch(Function<Void()> action) { pending.add(action) }
            }
            Void main() {
              var owner = Owner()
              var queue = DeliveryQueue()
              var parent = withContext<ResourceOwner, Task<Integer>>(value: owner, action: () {
                startTask(executor: queue) { 21 }
              })
              require(condition: parent.await() == 21, message: "parent result")
              require(condition: owner.registrations == 1 && owner.releases == 1, message: "completed parent released")
              var child = parent.then { queue.delivered = result }
              require(condition: owner.resources.size() == 1 && owner.registrations == 2, message: "child inherits owner outside its context")
              owner.close()
              for action : queue.pending { action() }
              require(condition: child.completed() && queue.delivered == 0, message: "closing owner cancels queued stage")
              require(condition: owner.resources.size() == 0 && owner.releases == 2, message: "cancelled child released once")
              var rejected = false
              try { parent.then { result + 1 } } catch Exception failure { rejected = failure.message == "owner closed" }
              require(condition: rejected, message: "closed owner cannot acquire a new stage")
              printLine("task-owner-ok")
            }
            """));
  }

  @Test
  void preservesNormExecutorRejectionAsATaskFailure() {
    assertEquals(
        "rejection-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.concurrent.TaskExecutor
            import std.core.Exception
            class ClosedExecutor implements TaskExecutor {
              Void dispatch(Function<Void()> action) { throw Exception(message: "closed executor") }
            }
            Void main() {
              var task = startTask(executor: ClosedExecutor()) { 1 }.then { result + 1 }
              var caught = false
              try { task.await() } catch Exception failure { caught = failure.message == "closed executor" }
              require(condition: caught, message: "original dispatch exception")
              task.close()
              printLine("rejection-ok")
            }
            """));
  }

  @Test
  void inheritsTheSelectedExecutorAcrossSuccessAndFailureStages() {
    assertEquals(
        "executor-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.concurrent.TaskExecutor
            import std.context.currentContext
            import std.context.withContext
            import std.core.Exception
            class Executor implements TaskExecutor {
              Void dispatch(Function<Void()> action) {
                startTask { withContext<String>(value: "delivery", action: action) }
                return
              }
            }
            Void main() {
              var task = startTask(executor: Executor()) {
                require(condition: currentContext<String>() == null, message: "work remains outside delivery context")
                21
              }.then {
                require(condition: currentContext<String>() == "delivery", message: "first stage uses executor")
                result * 2
              }.then<Integer> {
                require(condition: currentContext<String>() == "delivery", message: "executor is inherited")
                throw Exception(message: "handled")
              }.error {
                require(condition: currentContext<String>() == "delivery", message: "error uses inherited executor")
                42
              }
              require(condition: task.await() == 42, message: "delivered result")
              task.close()
              printLine("executor-ok")
            }
            """));
  }

  @Test
  void chainsTypedAndVoidCallbacksAndRecoversWorkAndCallbackFailures() {
    assertEquals(
        "chains-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.core.Exception
            class Seen { Integer value = 0 }
            Void main() {
              var seen = Seen()
              var chain = startTask { 20 }.then { result * 2 }.then { result + 2 }
              require(condition: chain.await() == 42, message: "typed stages")
              var write = chain.then { seen.value = result }
              write.await()
              require(condition: seen.value == 42, message: "void callback")
              var recovered = startTask<Integer> { throw Exception(message: "query") }
                .then { result + 1 }.error { 7 }
              require(condition: recovered.await() == 7, message: "work failure recovery")
              var failedCallback = chain.then<Integer> { throw Exception(message: "callback") }
                .error { seen.value = 9 }
              failedCallback.await()
              require(condition: seen.value == 9, message: "callback failure handling")
              printLine("chains-ok")
            }
            """));
  }

  @Test
  void startsVoidWorkWithAUnitCompletionAndPreservesFailure() {
    assertEquals(
        "void-task-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.concurrent.Task
            import std.core.Unit
            import std.core.Exception
            class Repository {
              Integer writes = 0
              Void save() { writes = writes + 1 }
              Void fail() { throw Exception(message: "write failed") }
            }
            Void main() {
              var repository = Repository()
              Task<Unit> saved = startTask { repository.save() }
              require(condition: saved.await() == Unit.Value && repository.writes == 1, message: "void work completes once")
              var failed = startTask(repository.fail)
              var caught = false
              try { failed.await() } catch Exception error { caught = error.message == "write failed" }
              require(condition: caught, message: "void work preserves failure")
              saved.close()
              failed.close()
              printLine("void-task-ok")
            }
            """));
  }

  @Test
  void startsTypedWorkAndPreservesObjectsCollectionsAndFailures() {
    assertEquals(
        "task-values-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.concurrent.startTask
            import std.core.Exception
            class Todo { String title }
            Void main() {
              var todo = Todo("学习 Norm")
              var task = startTask { [todo] }
              var result = task.await()
              require(condition: result.size() == 1 && result[0] == todo, message: "task preserves object identity")
              require(condition: result[0].title == "学习 Norm", message: "task preserves generic element types")
              var empty = startTask<String?> { null }
              require(condition: empty.await() == null, message: "nullable result")
              var failed = startTask<Integer> { throw Exception(message: "query failed") }
              var caught = false
              try { failed.await() } catch Exception error { caught = error.message == "query failed" }
              require(condition: caught, message: "original guest exception")
              task.close()
              empty.close()
              failed.close()
              printLine("task-values-ok")
            }
            """));
  }
}
