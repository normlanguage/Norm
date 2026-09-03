package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class GuestCallbackSchedulerTest {
  @Test
  @Timeout(5)
  void pumpsGuestCallbacksUntilCancellation() throws Exception {
    Thread owner = Thread.currentThread();
    AtomicReference<Object> result = new AtomicReference<>();
    try (GuestCallbackScheduler scheduler = new GuestCallbackScheduler()) {
      Thread callback =
          Thread.ofVirtual()
              .start(
                  () -> {
                    result.set(scheduler.invoke(() -> "handled"));
                    owner.interrupt();
                  });

      scheduler.runUntilCancellation();
      callback.join(2_000);

      assertEquals("handled", result.get());
      assertFalse(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @Timeout(5)
  void transfersExclusiveExecutionToTheCallingThread() throws Exception {
    Thread owner = Thread.currentThread();
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    try (GuestCallbackScheduler scheduler = new GuestCallbackScheduler()) {
      Thread callback =
          Thread.ofVirtual()
              .start(
                  () -> {
                    scheduler.invoke(
                        () -> {
                          callbackThread.set(Thread.currentThread());
                          return null;
                        });
                    owner.interrupt();
                  });

      scheduler.runUntilCancellation();
      callback.join(2_000);

      assertSame(callback, callbackThread.get());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @Timeout(5)
  void supportsNestedExecutionTransfers() throws Exception {
    Thread owner = Thread.currentThread();
    AtomicReference<Thread> outerExecution = new AtomicReference<>();
    AtomicReference<Thread> innerCaller = new AtomicReference<>();
    AtomicReference<Thread> innerExecution = new AtomicReference<>();
    try (GuestCallbackScheduler scheduler = new GuestCallbackScheduler()) {
      Thread outer =
          Thread.ofVirtual()
              .start(
                  () -> {
                    scheduler.invoke(
                        () -> {
                          outerExecution.set(Thread.currentThread());
                          Thread inner =
                              Thread.ofVirtual()
                                  .start(
                                      () -> {
                                        innerCaller.set(Thread.currentThread());
                                        scheduler.invoke(
                                            () -> {
                                              innerExecution.set(Thread.currentThread());
                                              return null;
                                            });
                                      });
                          scheduler.runUntil(() -> !inner.isAlive());
                          return null;
                        });
                    owner.interrupt();
                  });

      scheduler.runUntilCancellation();
      outer.join(2_000);

      assertSame(outer, outerExecution.get());
      assertSame(innerCaller.get(), innerExecution.get());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @Timeout(5)
  void returnsFromACallbackOnlyAfterTheOwnerReclaimsExecution() throws Exception {
    Thread owner = Thread.currentThread();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (GuestCallbackScheduler scheduler = new GuestCallbackScheduler()) {
      Thread callback =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      for (int invocation = 0; invocation < 1_000; invocation++) {
                        scheduler.invoke(() -> null);
                        if (scheduler.isBorrowedExecution()) {
                          throw new AssertionError(
                              "callback returned before ownership was reclaimed");
                        }
                      }
                    } catch (Throwable exception) {
                      failure.set(exception);
                    } finally {
                      owner.interrupt();
                    }
                  });

      scheduler.runUntilCancellation();
      callback.join(2_000);

      assertNull(failure.get());
    } finally {
      Thread.interrupted();
    }
  }
}
