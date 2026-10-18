package dev.w0fv1.norm.execution;

import java.util.Optional;

public record ExecutionUpdateEvent(
    long sequence,
    ExecutionUpdateEventKind kind,
    LiveExecutionStatus status,
    Optional<ExecutionUpdateResult> result) {
  public ExecutionUpdateEvent {
    if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
    kind = java.util.Objects.requireNonNull(kind, "kind");
    status = java.util.Objects.requireNonNull(status, "status");
    result = java.util.Objects.requireNonNull(result, "result");
  }
}
