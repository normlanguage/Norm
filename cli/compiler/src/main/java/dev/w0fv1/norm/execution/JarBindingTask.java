package dev.w0fv1.norm.execution;

public interface JarBindingTask extends AutoCloseable {
  java.util.concurrent.Executor continuationExecutor();

  java.util.Optional<java.util.concurrent.CompletionStage<Void>> ownedTermination();

  java.util.concurrent.CompletionStage<JarBindingResult> completion();

  default java.util.concurrent.CompletableFuture<Void> completionFuture() {
    var task = this;
    var future =
        new java.util.concurrent.CompletableFuture<Void>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            if (isDone()) return isCancelled();
            return task.cancel(mayInterruptIfRunning) && super.cancel(mayInterruptIfRunning);
          }
        };
    completion()
        .whenComplete(
            (value, failure) -> {
              if (failure == null) future.complete(null);
              else {
                while ((failure instanceof java.util.concurrent.CompletionException
                        || failure instanceof java.util.concurrent.ExecutionException)
                    && failure.getCause() != null) failure = failure.getCause();
                future.completeExceptionally(failure);
              }
            });
    return future;
  }

  JarBindingResult await();

  default boolean cancel() {
    return cancel(true);
  }

  boolean cancel(boolean mayInterruptIfRunning);

  boolean completed();

  @Override
  void close();
}
