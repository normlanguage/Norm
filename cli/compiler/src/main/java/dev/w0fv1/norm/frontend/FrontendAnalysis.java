package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.value.AnalysisResult;
import java.util.Objects;
import java.util.Optional;

record FrontendAnalysis(AnalysisResult analysis, Optional<BoundProgram> resolvedProgram) {
  FrontendAnalysis {
    Objects.requireNonNull(analysis, "analysis");
    resolvedProgram = Objects.requireNonNull(resolvedProgram, "resolvedProgram");
  }
}
