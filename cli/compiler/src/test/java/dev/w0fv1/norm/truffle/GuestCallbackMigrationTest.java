package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Timeout(
    value = 10,
    threadMode = org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD)
final class GuestCallbackMigrationTest {
  @Test
  void asynchronousCallbackIsRejectedBeforeGuestExecutionWithoutLockInversion() throws Exception {
    var entered = new AtomicBoolean();
    var stateLock = new ReentrantLock();
    stateLock.lock();
    try (var executor = Executors.newSingleThreadExecutor();
        var scheduler = new GuestCallbackScheduler();
        var migration = scheduler.migrationScope()) {
      ExecutionException failure =
          assertThrows(
              ExecutionException.class,
              () ->
                  scheduler.hostCall(
                      () -> {
                        var callback =
                            executor.submit(
                                () ->
                                    scheduler.invoke(
                                        () -> {
                                          entered.set(true);
                                          try {
                                            if (stateLock.tryLock(1, TimeUnit.SECONDS))
                                              stateLock.unlock();
                                          } catch (InterruptedException interruption) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(interruption);
                                          }
                                          return null;
                                        }));
                        return callback.get(2, TimeUnit.SECONDS);
                      }));
      assertInstanceOf(RuntimeValues.StateMigrationFailure.class, failure.getCause());
      assertFalse(entered.get());
      assertEquals("available", scheduler.invoke(() -> "available"));
    } finally {
      stateLock.unlock();
    }
  }

  @Test
  void synchronousCallbackKeepsTheSameDiscardableJournal() {
    var list = new RuntimeValues.ListValue(CoreType.DYNAMIC, new ArrayList<>(List.of(1)));
    try (var scheduler = new GuestCallbackScheduler();
        var scope = scheduler.migrationScope();
        var migration = RuntimeValues.prepareState(List.of(list), null)) {
      scheduler.hostCall(
          () ->
              scheduler.invoke(
                  () -> {
                    list.values.add(2);
                    return null;
                  }));
      assertEquals(List.of(1, 2), list.values);
    }
    assertEquals(List.of(1), list.values);
  }

  @Test
  void emptyPreparationDoesNotRejectAnOrdinaryQueuedCallback() throws Exception {
    var artifact =
        dev.w0fv1.norm.testing.NormTestKit.compile("Void main() {}")
            .output()
            .orElseThrow()
            .artifact();
    var program =
        new Lowerer(null)
            .lower(artifact, dev.w0fv1.norm.core.CoreExecutionPlan.forLiveArtifact(artifact));
    var execution = program.execution(dev.w0fv1.norm.execution.ExecutionContext.builder().build());
    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<Object> queued;
      var invoked = new CountDownLatch(1);
      try (var migration = RuntimeValues.prepareState(List.of(), execution)) {
        queued =
            executor.submit(
                () -> {
                  invoked.countDown();
                  return execution.callbacks().invoke(() -> 7);
                });
        assertTrue(invoked.await(2, TimeUnit.SECONDS));
      }
      assertEquals(7, execution.callbacks().hostCall(() -> queued.get(2, TimeUnit.SECONDS)));
    } finally {
      try {
        execution.close(null);
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void actualTransactionHostCallAutomaticallyRejectsCrossThreadCallbacks() throws Exception {
    var artifact =
        dev.w0fv1.norm.testing.NormTestKit.compile("Void main() {}")
            .output()
            .orElseThrow()
            .artifact();
    var program =
        new Lowerer(null)
            .lower(artifact, dev.w0fv1.norm.core.CoreExecutionPlan.forLiveArtifact(artifact));
    var execution = program.execution(dev.w0fv1.norm.execution.ExecutionContext.builder().build());
    var executor = Executors.newSingleThreadExecutor();
    try (var migration = RuntimeValues.prepareState(List.of(), execution)) {
      ExecutionException failure =
          assertThrows(
              ExecutionException.class,
              () ->
                  execution
                      .callbacks()
                      .hostCall(
                          () ->
                              executor
                                  .submit(() -> execution.callbacks().invoke(() -> 7))
                                  .get(2, TimeUnit.SECONDS)));
      assertInstanceOf(RuntimeValues.StateMigrationFailure.class, failure.getCause());
    } finally {
      try {
        execution.close(null);
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void ordinaryAsynchronousCallbackRemainsSupportedAfterNestedMigrationScopes() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor();
        var scheduler = new GuestCallbackScheduler()) {
      try (var outer = scheduler.migrationScope()) {
        try (var inner = scheduler.migrationScope()) {
          assertEquals(3, scheduler.hostCall(() -> scheduler.invoke(() -> 3)));
        }
      }
      assertEquals(
          7,
          scheduler.hostCall(
              () -> executor.submit(() -> scheduler.invoke(() -> 7)).get(2, TimeUnit.SECONDS)));
    }
  }
}
