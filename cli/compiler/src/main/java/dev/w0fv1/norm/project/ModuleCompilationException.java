package dev.w0fv1.norm.project;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public final class ModuleCompilationException extends IOException {
  private static final long serialVersionUID = 1L;
  private final transient List<Diagnostic> diagnostics;

  ModuleCompilationException(List<Diagnostic> diagnostics) {
    super(
        diagnostics.stream()
            .map(DiagnosticRenderer::render)
            .collect(Collectors.joining(System.lineSeparator())));
    this.diagnostics = List.copyOf(diagnostics);
  }

  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }
}
