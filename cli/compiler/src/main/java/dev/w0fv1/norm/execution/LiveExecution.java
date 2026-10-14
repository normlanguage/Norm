package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;

public interface LiveExecution extends PreparedExecution, AutoCloseable {
  void update(CoreArtifact artifact);

  void update(ExecutionRevision revision);

  default LiveExecutionStatus status() {
    return new LiveExecutionStatus(0, java.util.List.of());
  }

  @Override
  default void close() {}
}
