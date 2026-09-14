package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.Sha256Digest;
import java.util.Objects;

record CompilationHistory(
    IncrementalAnalysisPlan.History analysis, CoreBuildHistory core, Sha256Digest resultKey) {
  CompilationHistory {
    Objects.requireNonNull(analysis, "analysis");
    Objects.requireNonNull(core, "core");
    Objects.requireNonNull(resultKey, "resultKey");
  }
}
