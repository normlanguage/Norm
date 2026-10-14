package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveApplicationSessionTest {
  @TempDir Path directory;

  @Test
  void interruptedCallerWaitsForCompilationToStopBeforeReleasingResources() throws Exception {
    Path entry = directory.resolve("shutdown.norm");
    Files.writeString(entry, "Void main() { printLine(\"old\") }");
    var started = new CountDownLatch(1);
    var compiling = new CountDownLatch(1);
    var shuttingDown = new CountDownLatch(1);
    var releaseCompilation = new CountDownLatch(1);
    var compilations = new AtomicInteger();
    var interrupted = new AtomicBoolean();
    var failure = new AtomicReference<Throwable>();
    ExecutionBackend backend =
        new ExecutionBackend() {
          @Override
          public void execute(
              CoreArtifact artifact, CoreExecutionPlan plan, ExecutionContext context) {
            fail("watch execution must use prepareLive");
          }

          @Override
          public LiveExecution prepareLive(CoreArtifact artifact) {
            return new LiveExecution() {
              @Override
              public void execute(ExecutionContext context) {
                started.countDown();
                try {
                  assertTrue(compiling.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  throw new AssertionError(exception);
                }
                Thread.currentThread().interrupt();
                shuttingDown.countDown();
              }

              @Override
              public void update(CoreArtifact replacement) {
                fail("terminated application must not publish a pending compilation");
              }
            };
          }
        };
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var runner =
            new ApplicationRunner(
                environment.projectLoader(), environment.compilerSession(), backend)) {
      var execution =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      assertTrue(
                          runner
                              .runLive(
                                  entry,
                                  ExecutionContext.of(new PrintWriter(new StringWriter())),
                                  message -> {
                                    if (!message.equals("Compiling Norm sources")
                                        || compilations.incrementAndGet() != 2) return;
                                    compiling.countDown();
                                    boolean restoreInterrupt = false;
                                    while (releaseCompilation.getCount() != 0) {
                                      try {
                                        releaseCompilation.await();
                                      } catch (InterruptedException exception) {
                                        restoreInterrupt = true;
                                      }
                                    }
                                    if (restoreInterrupt) Thread.currentThread().interrupt();
                                  },
                                  message -> fail(message))
                              .isSuccess());
                      interrupted.set(Thread.currentThread().isInterrupted());
                    } catch (Throwable exception) {
                      failure.set(exception);
                    }
                  });
      try {
        assertTrue(started.await(30, TimeUnit.SECONDS));
        Files.writeString(entry, "Void main() { printLine(\"new\") }");
        assertTrue(shuttingDown.await(30, TimeUnit.SECONDS));
        assertFalse(execution.join(java.time.Duration.ofMillis(200)));
      } finally {
        releaseCompilation.countDown();
        execution.join(30000);
      }
      assertFalse(execution.isAlive());
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertTrue(interrupted.get());
    }
  }

  @Test
  void retainsRunningExecutionDuringCompilationFailureAndRecoversOnSave() throws Exception {
    Path entry = directory.resolve("main.norm");
    Files.writeString(entry, "Void main() { printLine(\"old\") }");
    var started = new CountDownLatch(1);
    var updated = new CountDownLatch(1);
    var invalid = new CountDownLatch(1);
    var stop = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    ExecutionBackend backend =
        new ExecutionBackend() {
          @Override
          public void execute(
              CoreArtifact artifact, CoreExecutionPlan plan, ExecutionContext context) {
            fail("watch execution must use prepareLive");
          }

          @Override
          public LiveExecution prepareLive(CoreArtifact artifact) {
            return new LiveExecution() {
              @Override
              public void execute(ExecutionContext context) {
                started.countDown();
                try {
                  assertTrue(stop.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new RuntimeException(exception);
                }
              }

              @Override
              public void update(CoreArtifact replacement) {
                assertNotEquals(artifact.entryDefinition(), replacement.entryDefinition());
                updated.countDown();
              }
            };
          }
        };
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var runner =
            new ApplicationRunner(
                environment.projectLoader(), environment.compilerSession(), backend)) {
      var execution =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      assertTrue(
                          runner
                              .runLive(
                                  entry,
                                  ExecutionContext.of(new PrintWriter(new StringWriter())),
                                  message -> {},
                                  message -> invalid.countDown())
                              .isSuccess());
                    } catch (Throwable exception) {
                      failure.set(exception);
                    }
                  });
      try {
        assertTrue(started.await(30, TimeUnit.SECONDS));
        Files.writeString(entry, "Void main() { missing() }");
        assertTrue(invalid.await(30, TimeUnit.SECONDS));
        assertEquals(1, updated.getCount());
        Files.writeString(entry, "Void main() { printLine(\"new\") }");
        assertTrue(updated.await(30, TimeUnit.SECONDS));
      } finally {
        stop.countDown();
        execution.join(30000);
      }
      assertFalse(execution.isAlive());
      if (failure.get() != null) throw new AssertionError(failure.get());
    }
  }
}
