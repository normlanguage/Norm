package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.util.List;
import java.util.Optional;

public record ExecutionUpdateResult(
    Outcome outcome,
    Optional<ArtifactId> candidate,
    LiveExecutionStatus status,
    List<Diagnostic> diagnostics,
    Optional<String> failure) {
  public enum Outcome {
    PUBLISHED,
    UNCHANGED,
    REJECTED,
    FAILED
  }

  public ExecutionUpdateResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
