package dev.w0fv1.norm.stdlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeUpdateEventsTest {
  @TempDir Path directory;

  @Test
  void runningNormSubscriptionReceivesPublishedDeclarationChanges() throws Exception {
    Path entry = directory.resolve("main.norm");
    Files.writeString(entry, source(1));
    var output = new StringWriter();
    var failure = new AtomicReference<Throwable>();
    try (var environment = dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(new NormRuntime());
        var runner = ApplicationRunner.open(environment);
        var session =
            runner.openSession(
                entry,
                ExecutionContext.of(new PrintWriter(output, true)),
                message -> {},
                message -> failure.set(new RuntimeException(message)))) {
      var execution =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      var result = session.execute();
                      if (!result.isSuccess())
                        failure.set(new RuntimeException(result.diagnostics().toString()));
                    } catch (Throwable exception) {
                      failure.set(exception);
                    }
                  });
      try {
        long ready = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!output.toString().contains("ready") && System.nanoTime() < ready) Thread.sleep(10);
        assertTrue(
            output.toString().contains("ready"),
            () ->
                "output="
                    + output
                    + ", alive="
                    + execution.isAlive()
                    + ", failure="
                    + failure.get());
        Files.writeString(entry, source(2));
        assertTrue(execution.join(Duration.ofSeconds(30)));
      } finally {
        session.cancel();
        execution.join(Duration.ofSeconds(30));
      }
      assertFalse(execution.isAlive());
      if (failure.get() != null) throw new AssertionError(failure.get());
    }
    assertTrue(output.toString().contains("Published"), () -> output.toString());
    assertTrue(output.toString().contains("true"), () -> output.toString());
  }

  private static String source(int marker) {
    return """
        import std.runtime.subscribeUpdates
        Integer marker() { %d }
        Void main() {
          printLine("ready")
          var subscription = subscribeUpdates()
          var event = subscription.next()
          if (event != null) {
            printLine(event.kind)
            printLine(event.changes.size() > 0)
          }
          subscription.close()
        }
        """
        .formatted(marker);
  }
}
