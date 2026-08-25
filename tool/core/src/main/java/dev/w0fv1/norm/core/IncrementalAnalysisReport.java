package dev.w0fv1.norm.core;

public record IncrementalAnalysisReport(
    int declarations, int analyzedDeclarations, int reusedDeclarations, long elapsedNanos) {
  public IncrementalAnalysisReport {
    if (declarations < 0
        || analyzedDeclarations < 0
        || reusedDeclarations < 0
        || elapsedNanos < 0) {
      throw new IllegalArgumentException("analysis measurements must not be negative");
    }
    if (analyzedDeclarations + reusedDeclarations != declarations) {
      throw new IllegalArgumentException("analysis outcomes must cover every declaration");
    }
  }

  public static IncrementalAnalysisReport analyzed(int declarations, long elapsedNanos) {
    return new IncrementalAnalysisReport(declarations, declarations, 0, elapsedNanos);
  }

  public static IncrementalAnalysisReport reused(int declarations) {
    return new IncrementalAnalysisReport(declarations, 0, declarations, 0);
  }
}
