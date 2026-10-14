package dev.w0fv1.norm.application;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.util.List;

public record ApplicationRunResult(boolean isSuccess, List<Diagnostic> diagnostics) {
  public ApplicationRunResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
