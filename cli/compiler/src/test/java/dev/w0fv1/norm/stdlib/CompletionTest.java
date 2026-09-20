package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
final class CompletionTest {
  @Test
  void completesTypedTasksFromAnotherThread() {
    assertOutput(
        JdkSystemPlatform.standard(),
        """
        import std.concurrent.completion
        import std.concurrent.startTask
        Void main() {
          var source = completion<Integer>()
          var task = source.task()
          var transformed = task.then((Integer value) { value + 1 })
          var producer = startTask { source.succeed(41) }
          printLine(transformed.await())
          printLine(producer.await())
          printLine(source.succeed(99))
          printLine(task.await())
          source.close()
        }
        """,
        "42",
        "true",
        "false",
        "41");
  }

  @Test
  void preservesFailureIdentityAndCancelsPendingTasksOnClose() {
    assertOutput(
        JdkSystemPlatform.standard(),
        """
        import std.concurrent.completion
        import std.core.Exception
        Void main() {
          var source = completion<String>()
          var task = source.task()
          var failure = Exception(message: "callback failed")
          source.fail(failure)
          try { task.await() }
          catch Exception error { printLine(error == failure) }
          var pending = completion<Integer>()
          var cancelled = pending.task()
          pending.close()
          printLine(cancelled.completed())
          printLine(pending.succeed(1))
          try { cancelled.await() }
          catch Exception error { printLine("cancelled") }
          source.close()
        }
        """,
        "true",
        "true",
        "false",
        "cancelled");
  }
}
