package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;

public interface ExecutionBackend {
  default void execute(CoreArtifact artifact, ExecutionContext context) {
    execute(artifact, dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(artifact), context);
  }

  void execute(
      CoreArtifact artifact,
      dev.w0fv1.norm.core.CoreExecutionPlan execution,
      ExecutionContext context);
}
