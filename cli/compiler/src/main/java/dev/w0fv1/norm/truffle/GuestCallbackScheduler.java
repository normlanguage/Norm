package dev.w0fv1.norm.truffle;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class GuestCallbackScheduler implements AutoCloseable {
  private final Thread rootExecutor = Thread.currentThread();
  private final ReentrantLock execution = new ReentrantLock(true);
  private final ThreadLocal<Integer> hostDepth = ThreadLocal.withInitial(() -> 0);
  private volatile boolean closed;

  GuestCallbackScheduler() {
    execution.lock();
  }

  Object invoke(Supplier<Object> operation) {
    execution.lock();
    try {
      if (closed) throw new IllegalStateException("Norm execution is closed");
      return operation.get();
    } finally {
      execution.unlock();
    }
  }

  <T, E extends Throwable> T hostCall(HostOperation<T, E> operation) throws E {
    requireOwner();
    int depth = execution.getHoldCount();
    int previousHostDepth = hostDepth.get();
    hostDepth.set(previousHostDepth + 1);
    for (int index = 0; index < depth; index++) execution.unlock();
    try {
      return operation.run();
    } finally {
      if (previousHostDepth == 0) hostDepth.remove();
      else hostDepth.set(previousHostDepth);
      for (int index = 0; index < depth; index++) execution.lock();
    }
  }

  void runUntil(BooleanSupplier completed) {
    hostCall(
        () -> {
          while (!completed.getAsBoolean()) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              if (completed.getAsBoolean()) return null;
              throw new IllegalStateException("Norm callback wait was interrupted", failure);
            }
          }
          return null;
        });
  }

  void runUntilCancellation() {
    try {
      runUntil(() -> Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  boolean isBorrowedExecution() {
    return execution.isHeldByCurrentThread()
        && (Thread.currentThread() != rootExecutor || hostDepth.get() > 0);
  }

  @FunctionalInterface
  interface HostOperation<T, E extends Throwable> {
    T run() throws E;
  }

  @Override
  public void close() {
    if (closed) return;
    requireOwner();
    closed = true;
    execution.unlock();
  }

  private void requireOwner() {
    if (!execution.isHeldByCurrentThread()) {
      throw new IllegalStateException("Norm callbacks must run on the execution thread");
    }
  }
}
