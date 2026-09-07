package dev.w0fv1.norm.core;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompilationResult(Optional<CompilationOutput> output, List<Diagnostic> diagnostics) {
  public CompilationResult {
    output = Objects.requireNonNull(output, "output");
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isSuccess() {
    return output.isPresent();
  }
}
