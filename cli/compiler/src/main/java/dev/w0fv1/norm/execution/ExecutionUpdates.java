package dev.w0fv1.norm.execution;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ExecutionUpdates {
  LiveExecutionStatus status();

  Optional<ExecutionUpdateResult> latest();

  CompletableFuture<ExecutionUpdateResult> request();

  Batch beginBatch();

  interface Batch extends AutoCloseable {
    CompletableFuture<ExecutionUpdateResult> commit();

    @Override
    void close();
  }
}
