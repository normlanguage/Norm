package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;

public interface ExecutionBackend {
  default LiveExecution prepareLive(CoreArtifact artifact) {
    throw new UnsupportedOperationException("execution backend does not support live execution");
  }

  default void execute(CoreArtifact artifact, ExecutionContext context) {
    execute(artifact, CoreExecutionPlan.forArtifact(artifact), context);
  }

  void execute(CoreArtifact artifact, CoreExecutionPlan execution, ExecutionContext context);
}
