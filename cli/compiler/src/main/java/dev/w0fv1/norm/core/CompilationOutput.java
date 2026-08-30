package dev.w0fv1.norm.core;

import java.util.Objects;

public record CompilationOutput(CoreArtifact artifact, CompilationState state) {
  public CompilationOutput {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(state, "state");
  }

  public CompilationOutput withDelta(CoreCompilationDelta delta) {
    return new CompilationOutput(artifact, state.withDelta(delta));
  }

  public CompilationOutput withAnalysisReport(IncrementalAnalysisReport report) {
    return new CompilationOutput(artifact, state.withAnalysisReport(report));
  }
}
