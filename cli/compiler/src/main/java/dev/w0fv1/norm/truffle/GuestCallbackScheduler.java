package dev.w0fv1.norm.truffle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class GuestCallbackScheduler implements AutoCloseable {
  private final Thread rootExecutor = Thread.currentThread();
  private Thread executor = rootExecutor;
  private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
  private boolean closed;

  Object invoke(Supplier<Object> operation) {
    Thread caller = Thread.currentThread();
    boolean direct;
    synchronized (this) {
      direct = caller == executor;
    }
    if (direct) return operation.get();
    Request request = new Request(caller);
    synchronized (this) {
      if (closed) throw new IllegalStateException("Norm execution is closed");
      requests.add(request);
    }
    request.awaitGrant();
    try {
      return operation.get();
    } finally {
      request.complete();
      request.awaitRelease();
    }
  }

  void runUntil(BooleanSupplier completed) {
    requireOwner();
    while (!completed.getAsBoolean() || !requests.isEmpty()) {
      Request request;
      try {
        request = requests.poll(10, TimeUnit.MILLISECONDS);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        if (completed.getAsBoolean()) return;
        throw new IllegalStateException("Norm callback wait was interrupted", failure);
      }
      if (request != null) transfer(request);
    }
  }

  void runUntilCancellation() {
    try {
      runUntil(() -> Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  boolean isBorrowedExecution() {
    synchronized (this) {
      return Thread.currentThread() == executor && executor != rootExecutor;
    }
  }

  @Override
  public void close() {
    List<Request> pending = new ArrayList<>();
    synchronized (this) {
      if (closed) return;
      closed = true;
      requests.drainTo(pending);
    }
    IllegalStateException failure = new IllegalStateException("Norm execution is closed");
    pending.forEach(request -> request.fail(failure));
  }

  private void requireOwner() {
    synchronized (this) {
      if (Thread.currentThread() == executor) return;
    }
    throw new IllegalStateException("Norm callbacks must run on the execution thread");
  }

  private void transfer(Request request) {
    Thread current = Thread.currentThread();
    synchronized (this) {
      if (executor != current) {
        throw new IllegalStateException("Norm execution ownership changed unexpectedly");
      }
      executor = request.caller;
      request.grant();
    }
    try {
      request.awaitCompletion();
      synchronized (this) {
        if (executor != request.caller) {
          throw new IllegalStateException("Norm execution ownership was not restored");
        }
        executor = current;
      }
    } finally {
      request.release();
    }
  }

  private static final class Request {
    private final Thread caller;
    private final CompletableFuture<Void> granted = new CompletableFuture<>();
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private final CompletableFuture<Void> released = new CompletableFuture<>();

    private Request(Thread caller) {
      this.caller = caller;
    }

    private void grant() {
      granted.complete(null);
    }

    private void complete() {
      completed.complete(null);
    }

    private void release() {
      released.complete(null);
    }

    private void fail(RuntimeException failure) {
      granted.completeExceptionally(failure);
      completed.completeExceptionally(failure);
    }

    private void awaitGrant() {
      await(granted, "Norm callback failed");
    }

    private void awaitCompletion() {
      await(completed, "Norm callback execution failed");
    }

    private void awaitRelease() {
      await(released, "Norm callback ownership release failed");
    }

    private static void await(CompletableFuture<Void> future, String message) {
      try {
        future.join();
      } catch (CompletionException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof RuntimeException exception) throw exception;
        if (cause instanceof Error error) throw error;
        throw new IllegalStateException(message, cause);
      }
    }
  }
}
