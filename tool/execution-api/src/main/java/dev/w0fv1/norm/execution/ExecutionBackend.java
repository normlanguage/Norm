package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;

public interface ExecutionBackend {
  void execute(CoreArtifact artifact, ExecutionContext context);
}
