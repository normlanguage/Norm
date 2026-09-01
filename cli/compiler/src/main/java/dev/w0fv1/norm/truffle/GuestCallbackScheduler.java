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
  private final Thread owner = Thread.currentThread();
  private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
  private boolean closed;

  Object invoke(Supplier<Object> operation) {
    if (Thread.currentThread() == owner) return operation.get();
    Request request = new Request(operation);
    synchronized (this) {
      if (closed) throw new IllegalStateException("Norm execution is closed");
      requests.add(request);
    }
    return request.await();
  }

  void runUntil(BooleanSupplier completed) {
    requireOwner();
    while (!completed.getAsBoolean() || !requests.isEmpty()) {
      Request request;
      try {
        request = requests.poll(10, TimeUnit.MILLISECONDS);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Norm callback wait was interrupted", failure);
      }
      if (request != null) request.run();
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
    if (Thread.currentThread() != owner) {
      throw new IllegalStateException("Norm callbacks must run on the execution thread");
    }
  }

  private static final class Request {
    private final Supplier<Object> operation;
    private final CompletableFuture<Object> result = new CompletableFuture<>();

    private Request(Supplier<Object> operation) {
      this.operation = operation;
    }

    private void run() {
      try {
        result.complete(operation.get());
      } catch (RuntimeException | Error failure) {
        result.completeExceptionally(failure);
      }
    }

    private void fail(RuntimeException failure) {
      result.completeExceptionally(failure);
    }

    private Object await() {
      try {
        return result.join();
      } catch (CompletionException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof RuntimeException exception) throw exception;
        if (cause instanceof Error error) throw error;
        throw new IllegalStateException("Norm callback failed", cause);
      }
    }
  }
}
