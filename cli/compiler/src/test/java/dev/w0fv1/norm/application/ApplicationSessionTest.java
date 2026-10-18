package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.execution.ExecutionUpdateEventKind;
import dev.w0fv1.norm.execution.ExecutionUpdatePreview;
import dev.w0fv1.norm.execution.ExecutionUpdateResult;
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
    var contexts = new java.util.concurrent.CopyOnWriteArrayList<ExecutionContext>();
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
            contexts.add(initial.context());
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
                contexts.add(candidate.context());
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
        var events = session.subscribeUpdates();
        try (var batch = session.beginBatch()) {
          assertThrows(IllegalStateException.class, session::beginBatch);
          assertThrows(IllegalStateException.class, session::requestUpdate);
          assertThrows(
              IllegalStateException.class,
              () -> contexts.getFirst().updates().orElseThrow().request());
          Files.writeString(helper, "package batch\nInteger value() { 2 }");
          assertFalse(published.await(250, TimeUnit.MILLISECONDS));
          Files.writeString(entry, "package batch\nVoid main() { printLine(value() + value()) }");
          var preview = batch.preview().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdatePreview.Outcome.PREVIEWED, preview.outcome());
          assertTrue(preview.candidate().isPresent());
          assertFalse(preview.changes().isEmpty());
          assertEquals(0, revisions.get());
          assertTrue(session.latestUpdate().isEmpty());
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdateResult.Outcome.PUBLISHED, update.outcome());
          assertTrue(update.candidate().isPresent());
          assertTrue(update.diagnostics().isEmpty());
          assertFalse(update.changes().isEmpty());
          assertEquals(
              ExecutionUpdateEventKind.REQUESTED,
              events.next(java.time.Duration.ofSeconds(30)).orElseThrow().kind());
          var publishedEvent = events.next(java.time.Duration.ofSeconds(30)).orElseThrow();
          assertEquals(ExecutionUpdateEventKind.PUBLISHED, publishedEvent.kind());
          assertTrue(publishedEvent.result().orElseThrow().changes().declarations().size() >= 1);
        }
        events.close();
        assertEquals(1, revisions.get());
        assertSame(
            contexts.getFirst().updates().orElseThrow(),
            contexts.getLast().updates().orElseThrow());
        assertEquals(
            ExecutionUpdateResult.Outcome.PUBLISHED,
            session.latestUpdate().orElseThrow().outcome());
        var directPreview = session.previewUpdate().get(30, TimeUnit.SECONDS);
        assertEquals(ExecutionUpdatePreview.Outcome.UNCHANGED, directPreview.outcome());
        assertEquals(1, revisions.get());
        try (var batch = session.beginBatch()) {
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdateResult.Outcome.UNCHANGED, update.outcome());
          assertEquals(1, update.status().revision());
        }
        assertEquals(1, revisions.get());
        try (var batch = session.beginBatch()) {
          Files.writeString(helper, "package batch\nInteger value() { 3 }");
          var preview = batch.preview().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdatePreview.Outcome.PREVIEWED, preview.outcome());
          Files.writeString(helper, "package batch\nInteger value() { 4 }");
          var stale = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdateResult.Outcome.REJECTED, stale.outcome());
          assertTrue(stale.failure().orElseThrow().contains("stale"));
          assertEquals(1, revisions.get());
        }
        Files.writeString(helper, "package batch\nInteger value() { 2 }");
        try (var batch = session.beginBatch()) {
          Files.writeString(entry, "package batch\nVoid main() { missing() }");
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdateResult.Outcome.REJECTED, update.outcome());
          assertFalse(update.diagnostics().isEmpty());
          assertEquals(1, update.status().revision());
        }
        assertEquals(1, revisions.get());
        assertEquals(
            ExecutionUpdateResult.Outcome.REJECTED, session.latestUpdate().orElseThrow().outcome());
        assertEquals(session.latestUpdate(), contexts.getFirst().updates().orElseThrow().latest());
        try (var batch = session.beginBatch()) {
          var update = batch.commit().get(30, TimeUnit.SECONDS);
          assertEquals(ExecutionUpdateResult.Outcome.REJECTED, update.outcome());
          assertFalse(update.diagnostics().isEmpty());
          assertEquals(1, update.status().revision());
        }
        Files.writeString(entry, "package batch\nVoid main() { printLine(value()) }");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (revisions.get() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(2, revisions.get());
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (session.latestUpdate().orElseThrow().outcome()
                != ExecutionUpdateResult.Outcome.PUBLISHED
            && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(
            ExecutionUpdateResult.Outcome.PUBLISHED,
            session.latestUpdate().orElseThrow().outcome());
        assertEquals(session.latestUpdate(), contexts.getFirst().updates().orElseThrow().latest());
      } finally {
        stop.countDown();
        execution.join(30000);
      }
      assertFalse(execution.isAlive());
      if (failure.get() != null) throw new AssertionError(failure.get());
    }
  }
}
