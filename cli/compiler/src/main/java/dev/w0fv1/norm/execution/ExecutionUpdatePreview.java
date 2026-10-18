package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.util.List;
import java.util.Optional;

public record ExecutionUpdatePreview(
    Outcome outcome,
    Optional<ArtifactId> candidate,
    LiveExecutionStatus status,
    List<Diagnostic> diagnostics,
    Optional<String> failure,
    RuntimeChangeSet changes) {
  public enum Outcome {
    PREVIEWED,
    UNCHANGED,
    REJECTED,
    FAILED
  }

  public ExecutionUpdatePreview {
    outcome = java.util.Objects.requireNonNull(outcome, "outcome");
    candidate = java.util.Objects.requireNonNull(candidate, "candidate");
    status = java.util.Objects.requireNonNull(status, "status");
    diagnostics = List.copyOf(diagnostics);
    failure = java.util.Objects.requireNonNull(failure, "failure");
    changes = java.util.Objects.requireNonNull(changes, "changes");
  }
}
