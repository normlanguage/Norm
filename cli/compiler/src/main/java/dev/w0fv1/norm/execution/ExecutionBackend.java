package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;

public interface ExecutionBackend {
  default void execute(CoreArtifact artifact, ExecutionContext context) {
    execute(artifact, CoreExecutionPlan.forArtifact(artifact), context);
  }

  void execute(CoreArtifact artifact, CoreExecutionPlan execution, ExecutionContext context);
}
