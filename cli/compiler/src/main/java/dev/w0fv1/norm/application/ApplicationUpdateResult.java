package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import java.util.List;
import java.util.Optional;

public record ApplicationUpdateResult(
    Outcome outcome,
    Optional<ArtifactId> candidate,
    LiveExecutionStatus status,
    List<Diagnostic> diagnostics,
    Optional<String> failure) {
  public enum Outcome {
    PUBLISHED,
    REJECTED,
    FAILED
  }

  public ApplicationUpdateResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
