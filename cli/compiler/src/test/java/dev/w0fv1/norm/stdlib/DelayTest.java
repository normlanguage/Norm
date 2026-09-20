package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
final class DelayTest {
  @Test
  void waitsAndRejectsNegativeDurations() {
    long started = System.nanoTime();
    assertOutput(
        JdkSystemPlatform.standard(),
        """
        import std.concurrent.delay
        import std.time.duration
        import std.core.Exception
        Void main() {
          delay(duration: duration(seconds: 0, nanoseconds: 50000000))
          delay(duration: duration(seconds: 0, nanoseconds: 0))
          try { delay(duration: duration(seconds: -1, nanoseconds: 0)) }
          catch Exception error { printLine("negative") }
          printLine("done")
        }
        """,
        "negative",
        "done");
    assertTrue(System.nanoTime() - started >= 50_000_000);
  }

  @Test
  void cancellingTaskInterruptsDelay() {
    assertOutput(
        JdkSystemPlatform.standard(),
        """
        import std.concurrent.delay
        import std.concurrent.startTask
        import std.time.duration
        Void main() {
          var task = startTask { delay(duration: duration(seconds: 300, nanoseconds: 0)) }
          delay(duration: duration(seconds: 0, nanoseconds: 50000000))
          task.cancel()
          task.termination()?.await()
          printLine(task.completed())
        }
        """,
        "true");
  }
}
