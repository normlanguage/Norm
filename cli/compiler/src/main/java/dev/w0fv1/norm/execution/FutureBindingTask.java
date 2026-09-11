package dev.w0fv1.norm.execution;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

public final class FutureBindingTask implements JarBindingTask {
  private enum ExecutionPhase {
    WAITING,
    RUNNING,
    FINISHED
  }

  private static final java.util.concurrent.Executor WORKERS =
      action -> Thread.ofVirtual().name("norm-task").start(action);

  public static java.util.concurrent.Executor workerExecutor() {
    return WORKERS;
  }

  private final Future<?> source;
  private final CompletionStage<Void> termination;
  private final java.util.concurrent.Executor continuationExecutor;
  private final CompletionStage<?> notification;
  private final Function<Object, JarBindingResult> conversion;
  private final Function<Object, Object> hostConversion;
  private CompletableFuture<Object> exported;
  private final CompletableFuture<JarBindingResult> result = new CompletableFuture<>();
  private boolean observing;

  public FutureBindingTask(Object host, Function<Object, JarBindingResult> conversion) {
    this(
        host instanceof CompletionStage<?> stage ? stage.toCompletableFuture() : (Future<?>) host,
        null,
        conversion,
        null,
        WORKERS,
        null);
  }

  private FutureBindingTask(
      Future<?> source,
      CompletionStage<?> notification,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion,
      java.util.concurrent.Executor continuationExecutor,
      CompletionStage<Void> termination) {
    this.source = Objects.requireNonNull(source, "source");
    this.termination = termination;
    this.notification =
        notification != null
            ? notification
            : source instanceof CompletionStage<?> stage ? stage : null;
    this.conversion = Objects.requireNonNull(conversion, "conversion");
    this.hostConversion = hostConversion;
    this.continuationExecutor =
        Objects.requireNonNull(continuationExecutor, "continuationExecutor");
  }

  public static FutureBindingTask start(
      Callable<?> work, Function<Object, JarBindingResult> conversion) {
    return start(work, conversion, Function.identity());
  }

  public static FutureBindingTask start(
      Callable<?> work,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion) {
    return start(work, conversion, hostConversion, WORKERS);
  }

  public static FutureBindingTask start(
      Callable<?> work,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion,
      java.util.concurrent.Executor continuationExecutor) {
    return create(
        (action, rejected) -> WORKERS.execute(action),
        work,
        conversion,
        hostConversion,
        continuationExecutor);
  }

  public static FutureBindingTask after(
      CompletionStage<?> prerequisite,
      Callable<?> work,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion) {
    return after(prerequisite, work, conversion, hostConversion, WORKERS);
  }

  public static FutureBindingTask after(
      CompletionStage<?> prerequisite,
      Callable<?> work,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion,
      java.util.concurrent.Executor continuationExecutor) {
    Objects.requireNonNull(prerequisite, "prerequisite");
    return create(
        (action, rejected) ->
            prerequisite.whenComplete(
                (value, failure) -> {
                  try {
                    continuationExecutor.execute(action);
                  } catch (RuntimeException rejection) {
                    rejected.accept(rejection);
                  }
                }),
        work,
        conversion,
        hostConversion,
        continuationExecutor);
  }

  private static FutureBindingTask create(
      java.util.function.BiConsumer<Runnable, java.util.function.Consumer<Throwable>> executor,
      Callable<?> work,
      Function<Object, JarBindingResult> conversion,
      Function<Object, Object> hostConversion,
      java.util.concurrent.Executor continuationExecutor) {
    Objects.requireNonNull(work, "work");
    Objects.requireNonNull(hostConversion, "hostConversion");
    var notification = new CompletableFuture<Object>();
    var termination = new CompletableFuture<Void>();
    var phase = new java.util.concurrent.atomic.AtomicReference<>(ExecutionPhase.WAITING);
    var source =
        new FutureTask<Object>(work::call) {
          @Override
          public void run() {
            if (!phase.compareAndSet(ExecutionPhase.WAITING, ExecutionPhase.RUNNING)) return;
            try {
              super.run();
            } finally {
              phase.set(ExecutionPhase.FINISHED);
              termination.complete(null);
            }
          }

          void reject(Throwable failure) {
            setException(failure);
          }

          @Override
          protected void done() {
            try {
              notification.complete(get());
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              notification.completeExceptionally(failure);
            } catch (ExecutionException | CancellationException failure) {
              notification.completeExceptionally(failure);
            } finally {
              if (phase.compareAndSet(ExecutionPhase.WAITING, ExecutionPhase.FINISHED)) {
                termination.complete(null);
              }
            }
          }
        };
    var task =
        new FutureBindingTask(
            source,
            notification,
            conversion,
            hostConversion,
            continuationExecutor,
            termination.minimalCompletionStage());
    try {
      executor.accept(source, source::reject);
    } catch (RuntimeException rejection) {
      source.reject(rejection);
    }
    return task;
  }

  @Override
  public java.util.concurrent.Executor continuationExecutor() {
    return continuationExecutor;
  }

  public java.util.Optional<CompletionStage<Void>> ownedTermination() {
    return java.util.Optional.ofNullable(termination);
  }

  @Override
  public synchronized CompletionStage<JarBindingResult> completion() {
    if (!observing) {
      observing = true;
      if (notification != null) {
        notification.whenComplete(this::settle);
      } else {
        Thread.ofVirtual()
            .name("norm-java-task")
            .start(
                () -> {
                  try {
                    settle(source.get(), null);
                  } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    settle(null, failure);
                  } catch (Exception failure) {
                    settle(null, failure);
                  }
                });
      }
    }
    return result.minimalCompletionStage();
  }

  private void settle(Object value, Throwable failure) {
    if (failure != null) {
      result.completeExceptionally(unwrapFailure(failure));
      return;
    }
    try {
      result.complete(conversion.apply(value));
    } catch (Throwable conversionFailure) {
      result.completeExceptionally(conversionFailure);
    }
  }

  private static Throwable unwrapFailure(Throwable failure) {
    while ((failure instanceof CompletionException || failure instanceof ExecutionException)
        && failure.getCause() != null) {
      failure = failure.getCause();
    }
    return failure;
  }

  @Override
  public JarBindingResult await() {
    completion();
    try {
      return result.get();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new JarBindingInvocationException("Java task await was interrupted", failure);
    } catch (ExecutionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof JarBindingCallbackException callback) throw callback.failure();
      throw new JarBindingInvocationException("Java task completed exceptionally", cause);
    } catch (CancellationException failure) {
      throw new JarBindingInvocationException("Java task was cancelled", failure);
    }
  }

  @Override
  public boolean cancel() {
    return source.cancel(true);
  }

  @Override
  public boolean completed() {
    return source.isDone();
  }

  @Override
  public synchronized Object hostValue() {
    if (hostConversion == null) return source;
    if (exported == null) {
      exported =
          new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
              if (!source.cancel(mayInterruptIfRunning)) return false;
              return super.cancel(mayInterruptIfRunning) || isCancelled();
            }
          };
      var target = exported;
      notification.whenComplete(
          (value, failure) -> {
            if (target.isDone()) return;
            if (failure != null) {
              target.completeExceptionally(unwrapFailure(failure));
              return;
            }
            try {
              target.complete(hostConversion.apply(value));
            } catch (Throwable conversionFailure) {
              target.completeExceptionally(conversionFailure);
            }
          });
    }
    return exported;
  }

  @Override
  public void close() {
    if (!source.isDone()) source.cancel(true);
  }
}
