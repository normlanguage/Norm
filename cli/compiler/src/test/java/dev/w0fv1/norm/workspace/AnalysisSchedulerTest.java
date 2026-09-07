package dev.w0fv1.norm.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AnalysisSchedulerTest {
  @Test
  void coalescesPendingWorkAndCancelsTheRunningRevision() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try (var scheduler = new AnalysisScheduler<String>()) {
      var first =
          scheduler.submit(
              "project",
              token -> {
                started.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                assertTrue(token.isCancellationRequested());
              });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      var second = scheduler.submit("project", token -> calls.addAndGet(100));
      var third = scheduler.submit("project", token -> calls.incrementAndGet());
      assertTrue(first.isCancelled());
      assertTrue(second.isCancelled());
      release.countDown();
      third.get(10, TimeUnit.SECONDS);
      assertEquals(1, calls.get());
    } finally {
      release.countDown();
    }
  }

  @Test
  void runsIndependentProjectsWhileOneProjectIsBlocked() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var scheduler = new AnalysisScheduler<String>()) {
      scheduler.submit(
          "first",
          token -> {
            started.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
          });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      scheduler.submit("second", token -> {}).get(10, TimeUnit.SECONDS);
      release.countDown();
      scheduler.settled().get(10, TimeUnit.SECONDS);
    } finally {
      release.countDown();
    }
  }

  @Test
  void completesFailedJobsAndContinuesAcceptingWork() throws Exception {
    try (var scheduler = new AnalysisScheduler<String>()) {
      var failure =
          scheduler.submit(
              "project",
              token -> {
                throw new IllegalStateException("failed");
              });
      assertThrows(
          java.util.concurrent.ExecutionException.class, () -> failure.get(10, TimeUnit.SECONDS));
      scheduler.submit("project", token -> {}).get(10, TimeUnit.SECONDS);
    }
  }
}
