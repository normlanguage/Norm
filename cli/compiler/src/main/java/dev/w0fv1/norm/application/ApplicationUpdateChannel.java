package dev.w0fv1.norm.application;

import dev.w0fv1.norm.execution.ExecutionUpdateResult;
import dev.w0fv1.norm.execution.ExecutionUpdateSubscription;
import dev.w0fv1.norm.execution.ExecutionUpdates;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class ApplicationUpdateChannel implements ExecutionUpdates {
  private volatile LiveApplicationSession session;

  synchronized void bind(LiveApplicationSession value) {
    if (session != null) throw new IllegalStateException("application updates are already bound");
    session = java.util.Objects.requireNonNull(value);
  }

  private LiveApplicationSession session() {
    return java.util.Objects.requireNonNull(session, "application updates are not bound");
  }

  @Override
  public LiveExecutionStatus status() {
    return session().status();
  }

  @Override
  public Optional<ExecutionUpdateResult> latest() {
    return session().latest();
  }

  @Override
  public CompletableFuture<ExecutionUpdateResult> request() {
    return session().requestUpdate();
  }

  @Override
  public ExecutionUpdateSubscription subscribe(long afterSequence) {
    return session().subscribe(afterSequence);
  }

  @Override
  public Batch beginBatch() {
    return session().beginBatch();
  }
}
