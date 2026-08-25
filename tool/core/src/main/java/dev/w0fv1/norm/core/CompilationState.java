package dev.w0fv1.norm.core;

public record CompilationState(
    CoreBuildReport buildReport,
    CoreDependencyIndex dependencies,
    CoreCompilationDelta delta,
    IncrementalAnalysisReport analysisReport) {
  public CompilationState {
    java.util.Objects.requireNonNull(buildReport, "buildReport");
    java.util.Objects.requireNonNull(dependencies, "dependencies");
    java.util.Objects.requireNonNull(delta, "delta");
    java.util.Objects.requireNonNull(analysisReport, "analysisReport");
  }

  public CompilationState withDelta(CoreCompilationDelta delta) {
    return new CompilationState(buildReport, dependencies, delta, analysisReport);
  }

  public CompilationState withAnalysisReport(IncrementalAnalysisReport analysisReport) {
    return new CompilationState(buildReport, dependencies, delta, analysisReport);
  }
}
