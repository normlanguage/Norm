package dev.w0fv1.norm.execution;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ExecutionUpdates {
  LiveExecutionStatus status();

  Optional<ExecutionUpdateResult> latest();

  CompletableFuture<ExecutionUpdateResult> request();

  CompletableFuture<ExecutionUpdatePreview> preview();

  default ExecutionUpdateSubscription subscribe() {
    return subscribe(0);
  }

  ExecutionUpdateSubscription subscribe(long afterSequence);

  default ExecutionUpdateSubscription onUpdate(Consumer<ExecutionUpdateEvent> listener) {
    return onUpdate(0, listener);
  }

  ExecutionUpdateSubscription onUpdate(long afterSequence, Consumer<ExecutionUpdateEvent> listener);

  Batch beginBatch();

  interface Batch extends AutoCloseable {
    CompletableFuture<ExecutionUpdatePreview> preview();

    CompletableFuture<ExecutionUpdateResult> commit();

    @Override
    void close();
  }
}
