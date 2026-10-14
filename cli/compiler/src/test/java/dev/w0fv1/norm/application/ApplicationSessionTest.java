package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationSessionTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void batchPublishesOneCompleteTreeAndReturnsStructuredCompilationFailure(boolean progressFailure)
      throws Exception {
    Path module = Files.createDirectories(directory.resolve("batch"));
    Files.writeString(
        module.resolve("module.norm"), "Module module() { module(name: \"batch\", version: 1) }");
    Path entry = module.resolve("application.norm");
    Path helper = module.resolve("value.norm");
    Files.writeString(entry, "package batch\nVoid main() { printLine(value()) }");
    Files.writeString(helper, "package batch\nInteger value() { 1 }");
    var started = new CountDownLatch(1);
    var published = new CountDownLatch(1);
    var stop = new CountDownLatch(1);
    var revisions = new AtomicInteger();
    var failure = new AtomicReference<Throwable>();
    ExecutionBackend backend =
        new ExecutionBackend() {
          @Override
          public void execute(
              CoreArtifact artifact, CoreExecutionPlan plan, ExecutionContext context) {
            fail("source execution requires a live revision");
          }

          @Override
          public LiveExecution prepareLive(ExecutionRevision initial) {
            var current = new AtomicReference<>(initial);
            return new LiveExecution() {
              @Override
              public void execute(ExecutionContext context) {
                started.countDown();
                try {
                  assertTrue(stop.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  throw new AssertionError(exception);
                }
              }

              @Override
              public void update(CoreArtifact artifact) {
                fail("source update requires a complete revision");
              }

              @Override
              public void update(ExecutionRevision candidate) {
                current.getAndSet(candidate).close();
                revisions.incrementAndGet();
                published.countDown();
              }

              @Override
              public LiveExecutionStatus status() {
                return new LiveExecutionStatus(revisions.get(), List.of());
              }

              @Override
              public void close() {
                current.getAndSet(null).close();
              }
            };
          }
        };
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var runner =
            new ApplicationRunner(
                environment.projectLoader(), environment.compilerSession(), backend);
        var session =
            runner.openSession(
                entry,
                ExecutionContext.builder().build(),
                message -> {
                  if (progressFailure && message.equals("Published runtime revision"))
                    throw new IllegalArgumentException("observer failure");
                },
                message -> {})) {
      var execution =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      assertTrue(session.execute().isSuccess());
                    } catch (Throwable exception) {
                      failure.set(exception);
                    }
                  });
      try {
        assertTrue(started.await(30, TimeUnit.SECONDS));
        try (var batch = session.beginBatch()) {
          Files.writeString(helper, "package batch\nInteger value() { 2 }");
          assertFalse(published.await(250, TimeUnit.MILLISECONDS));
          Files.writeString(entry, "package batch\nVoid main() { printLine(value() + value()) }");
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ApplicationUpdateResult.Outcome.PUBLISHED, update.outcome());
          assertTrue(update.candidate().isPresent());
          assertTrue(update.diagnostics().isEmpty());
        }
        assertEquals(1, revisions.get());
        try (var batch = session.beginBatch()) {
          Files.writeString(entry, "package batch\nVoid main() { missing() }");
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ApplicationUpdateResult.Outcome.REJECTED, update.outcome());
          assertFalse(update.diagnostics().isEmpty());
          assertEquals(1, update.status().revision());
        }
        assertEquals(1, revisions.get());
      } finally {
        stop.countDown();
        execution.join(30000);
      }
      assertFalse(execution.isAlive());
      if (failure.get() != null) throw new AssertionError(failure.get());
    }
  }
}
