package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;

public interface LiveExecution extends PreparedExecution {
  void update(CoreArtifact artifact);
}
