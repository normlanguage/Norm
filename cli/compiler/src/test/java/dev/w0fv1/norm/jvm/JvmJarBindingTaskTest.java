package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.FutureBindingTask;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class JvmJarBindingTaskTest {
  @Test
  void distinguishesCancellationFromTheEndOfWorkerCleanup() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var cleaning = new java.util.concurrent.CountDownLatch(1);
    var releaseCleanup = new java.util.concurrent.CountDownLatch(1);
    var task =
        FutureBindingTask.start(
            () -> {
              entered.countDown();
              try {
                new java.util.concurrent.CountDownLatch(1).await();
              } finally {
                cleaning.countDown();
                releaseCleanup.await();
              }
              return "finished";
            },
            JarBindingResult.Scalar::new);
    try {
      var terminated = task.ownedTermination().orElseThrow().toCompletableFuture();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertTrue(task.cancel());
      assertTrue(cleaning.await(5, TimeUnit.SECONDS));
      assertTrue(task.completed());
      assertFalse(terminated.isDone());
      releaseCleanup.countDown();
      terminated.get(5, TimeUnit.SECONDS);
    } finally {
      releaseCleanup.countDown();
      task.close();
    }
  }

  @Test
  void finishesUnstartedCancelledAndRejectedExecutions() throws Exception {
    var ready = new CompletableFuture<String>();
    var cancelled = FutureBindingTask.after(ready, () -> "never", JarBindingResult.Scalar::new);
    assertTrue(cancelled.cancel());
    cancelled.ownedTermination().orElseThrow().toCompletableFuture().get(5, TimeUnit.SECONDS);
    var rejected =
        FutureBindingTask.after(
            ready,
            () -> "never",
            JarBindingResult.Scalar::new,
            action -> {
              throw new java.util.concurrent.RejectedExecutionException("closed");
            });
    ready.complete("ready");
    rejected.ownedTermination().orElseThrow().toCompletableFuture().get(5, TimeUnit.SECONDS);
    assertTrue(rejected.completed());
    assertTrue(
        FutureBindingTask.fromCompletion(
                CompletableFuture.completedFuture("host"), JarBindingResult.Scalar::new)
            .ownedTermination()
            .isEmpty());
  }

  @Test
  void deliversDependentWorkOnTheSelectedQueueAndCancelsQueuedActions() throws Exception {
    var queue = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
    var deliveryThread = Thread.currentThread();
    var parent = FutureBindingTask.start(() -> "ready", JarBindingResult.Scalar::new, queue::add);
    var calls = new AtomicInteger();
    var child =
        FutureBindingTask.after(
            parent.completion(),
            () -> {
              assertSame(deliveryThread, Thread.currentThread());
              calls.incrementAndGet();
              return "delivered";
            },
            JarBindingResult.Scalar::new,
            parent.continuationExecutor());
    var action = queue.poll(5, TimeUnit.SECONDS);
    assertNotNull(action);
    assertEquals(0, calls.get());
    assertFalse(child.completed());
    action.run();
    assertEquals(new JarBindingResult.Scalar("delivered"), child.await());
    var cancelled =
        FutureBindingTask.after(
            child.completion(),
            () -> {
              calls.addAndGet(100);
              return "cancelled";
            },
            JarBindingResult.Scalar::new,
            child.continuationExecutor());
    var queued = queue.poll(5, TimeUnit.SECONDS);
    assertNotNull(queued);
    assertTrue(cancelled.cancel());
    queued.run();
    assertEquals(1, calls.get());
    parent.close();
    child.close();
    cancelled.close();
  }

  @Test
  void failsTheStageWhenItsExecutorRejectsDelivery() {
    var ready = new CompletableFuture<String>();
    var rejection = new java.util.concurrent.RejectedExecutionException("closed executor");
    var task =
        FutureBindingTask.after(
            ready,
            () -> "unreachable",
            JarBindingResult.Scalar::new,
            action -> {
              throw rejection;
            });
    ready.complete("ready");
    var failure =
        assertThrows(
            java.util.concurrent.ExecutionException.class,
            () -> task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
    assertSame(rejection, failure.getCause());
    assertTrue(task.completed());
    task.close();
  }

  @Test
  void startsDependentWorkOnlyAfterCompletionAndHonorsEarlierCancellation() throws Exception {
    var ready = new CompletableFuture<String>();
    var calls = new AtomicInteger();
    var task =
        FutureBindingTask.after(
            ready,
            () -> {
              assertTrue(Thread.currentThread().isVirtual());
              calls.incrementAndGet();
              return ready.join();
            },
            JarBindingResult.Scalar::new);
    var cancelled =
        FutureBindingTask.after(
            ready,
            () -> {
              calls.addAndGet(100);
              return "cancelled";
            },
            JarBindingResult.Scalar::new);
    assertEquals(0, calls.get());
    assertFalse(task.completed());
    assertTrue(cancelled.cancel());
    ready.complete("ready");
    assertEquals(
        new JarBindingResult.Scalar("ready"),
        task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
    assertThrows(JarBindingInvocationException.class, cancelled::await);
    assertEquals(1, calls.get());
    task.close();
    cancelled.close();
  }

  @Test
  void startsWorkOnAVirtualThreadAndCancelsTheActualWorker() throws Exception {
    var started = new CompletableFuture<Boolean>();
    var interrupted = new CompletableFuture<Boolean>();
    var gate = new java.util.concurrent.CountDownLatch(1);
    var task =
        FutureBindingTask.start(
            () -> {
              started.complete(Thread.currentThread().isVirtual());
              try {
                gate.await();
                return "finished";
              } catch (InterruptedException failure) {
                interrupted.complete(true);
                throw failure;
              }
            },
            JarBindingResult.Scalar::new);
    try {
      assertTrue(started.get(5, TimeUnit.SECONDS));
      assertFalse(task.completed());
      var notification = task.completion().toCompletableFuture();
      assertTrue(task.cancel());
      assertTrue(interrupted.get(5, TimeUnit.SECONDS));
      assertTrue(task.completed());
      assertThrows(CompletionException.class, notification::join);
    } finally {
      gate.countDown();
      task.close();
    }
  }

  @Test
  void convertsSubmittedResultsOnceAndPreservesWorkFailure() throws Exception {
    var calls = new AtomicInteger();
    var task =
        FutureBindingTask.start(
            () -> "Todo",
            value -> {
              calls.incrementAndGet();
              return new JarBindingResult.Scalar(value);
            });
    var result = task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
    assertEquals(new JarBindingResult.Scalar("Todo"), result);
    assertSame(result, task.await());
    assertEquals(1, calls.get());
    var failure = new IllegalStateException("query failed");
    var failed =
        FutureBindingTask.start(
            () -> {
              throw failure;
            },
            JarBindingResult.Scalar::new);
    assertSame(failure, assertThrows(JarBindingInvocationException.class, failed::await).failure());
    var failedResult = failed.completion();
    assertSame(
        failure,
        assertThrows(CompletionException.class, () -> failedResult.toCompletableFuture().join())
            .getCause());
    task.close();
    failed.close();
  }

  @Test
  void supportsCompletionSignalsAndNotifiesCancellationOfTheirTasks() throws Exception {
    var view = CompletableFuture.completedFuture("任务").minimalCompletionStage();
    var completed = FutureBindingTask.fromCompletion(view, JarBindingResult.Scalar::new);
    assertTrue(completed.completed());
    assertEquals(new JarBindingResult.Scalar("任务"), completed.await());
    var source = new CompletableFuture<String>();
    var pending =
        FutureBindingTask.fromCompletion(
            source.minimalCompletionStage(), JarBindingResult.Scalar::new);
    var notification = pending.completion().toCompletableFuture();
    assertTrue(pending.cancel());
    assertTrue(notification.isDone());
    assertThrows(CompletionException.class, notification::join);
    assertThrows(JarBindingInvocationException.class, pending::await);
    assertFalse(source.isCancelled());
  }

  @Test
  void sharesConvertedResultWithEarlyAndLateSubscribersAndAwait() throws Exception {
    var source = new CompletableFuture<String>();
    var conversions = new AtomicInteger();
    var task =
        FutureBindingTask.fromCompletion(
            source,
            value -> {
              conversions.incrementAndGet();
              return new JarBindingResult.Scalar(value);
            });
    var early = task.completion().toCompletableFuture();
    assertFalse(early.isDone());
    source.complete("任务");
    var result = early.get(5, TimeUnit.SECONDS);
    assertSame(result, task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
    assertSame(result, task.await());
    assertEquals(1, conversions.get());
  }

  @Test
  void isolatesSubscribersFromTaskOwnershipAndEachOther() {
    var source = new CompletableFuture<String>();
    var task = FutureBindingTask.fromCompletion(source, JarBindingResult.Scalar::new);
    var cancelledSubscription = task.completion().toCompletableFuture();
    cancelledSubscription.cancel(true);
    assertFalse(source.isCancelled());
    var callbackFailure = new IllegalStateException("callback failed");
    var failedSubscription =
        task.completion()
            .thenApply(
                value -> {
                  throw callbackFailure;
                });
    source.complete("任务");
    assertSame(
        callbackFailure,
        assertThrows(
                CompletionException.class, () -> failedSubscription.toCompletableFuture().join())
            .getCause());
    assertEquals(new JarBindingResult.Scalar("任务"), task.await());
  }

  @Test
  void sharesConversionFailureWithoutRepeatingConversion() {
    var conversions = new AtomicInteger();
    var failure = new IllegalArgumentException("invalid result");
    var task =
        FutureBindingTask.fromCompletion(
            CompletableFuture.completedFuture("任务"),
            value -> {
              conversions.incrementAndGet();
              throw failure;
            });
    assertSame(
        failure,
        assertThrows(
                CompletionException.class, () -> task.completion().toCompletableFuture().join())
            .getCause());
    assertSame(failure, assertThrows(JarBindingInvocationException.class, task::await).failure());
    assertEquals(1, conversions.get());
  }

  @Test
  void preservesFailureAndCancellationAcrossBothResultApis() {
    var source = new CompletableFuture<String>();
    var task = FutureBindingTask.fromCompletion(source, JarBindingResult.Scalar::new);
    var failure = new IllegalStateException("查询失败");
    source.completeExceptionally(new CompletionException(failure));
    assertSame(
        failure,
        assertThrows(
                CompletionException.class, () -> task.completion().toCompletableFuture().join())
            .getCause());
    assertSame(failure, assertThrows(JarBindingInvocationException.class, task::await).failure());
    var pending = new CompletableFuture<String>();
    var cancelled = FutureBindingTask.fromCompletion(pending, JarBindingResult.Scalar::new);
    var notification = cancelled.completion().toCompletableFuture();
    cancelled.close();
    assertTrue(pending.isCancelled());
    assertTrue(cancelled.completed());
    assertThrows(CompletionException.class, notification::join);
    assertThrows(JarBindingInvocationException.class, cancelled::await);
  }

  @Test
  void completionFuturePreservesFailureAndBidirectionalCancellation() {
    var source = new CompletableFuture<String>();
    var task = FutureBindingTask.fromCompletion(source, JarBindingResult.Scalar::new);
    var future = task.completionFuture();
    source.complete("done");
    assertNull(future.join());
    assertFalse(future.cancel(true));
    var original = new IllegalStateException("original");
    var failedSource = new CompletableFuture<String>();
    var failed =
        FutureBindingTask.fromCompletion(failedSource, JarBindingResult.Scalar::new)
            .completionFuture();
    failedSource.completeExceptionally(original);
    assertSame(original, assertThrows(CompletionException.class, failed::join).getCause());
    var hostCancelledSource = new CompletableFuture<String>();
    var hostCancelled =
        FutureBindingTask.fromCompletion(hostCancelledSource, JarBindingResult.Scalar::new)
            .completionFuture();
    assertTrue(hostCancelled.cancel(true));
    assertTrue(hostCancelledSource.isCancelled());
    var guestCancelledSource = new CompletableFuture<String>();
    var guestCancelledTask =
        FutureBindingTask.fromCompletion(guestCancelledSource, JarBindingResult.Scalar::new);
    var guestCancelled = guestCancelledTask.completionFuture();
    guestCancelledTask.cancel();
    assertTrue(guestCancelled.isCancelled());
    var closedSource = new CompletableFuture<String>();
    var closedTask = FutureBindingTask.fromCompletion(closedSource, JarBindingResult.Scalar::new);
    var closed = closedTask.completionFuture();
    closedTask.close();
    assertTrue(closed.isCancelled());
  }

  @Test
  void javaCompletionCancellationInterruptsRunningWorkAndWaitsForCleanup() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var cleaned = new java.util.concurrent.CountDownLatch(1);
    var task =
        FutureBindingTask.start(
            () -> {
              entered.countDown();
              try {
                new java.util.concurrent.CountDownLatch(1).await();
              } finally {
                cleaned.countDown();
              }
              return "unreachable";
            },
            JarBindingResult.Scalar::new);
    try {
      var future = task.completionFuture();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertTrue(future.cancel(true));
      assertTrue(cleaned.await(5, TimeUnit.SECONDS));
      task.ownedTermination().orElseThrow().toCompletableFuture().get(5, TimeUnit.SECONDS);
      assertTrue(future.isCancelled());
    } finally {
      task.close();
    }
  }
}
