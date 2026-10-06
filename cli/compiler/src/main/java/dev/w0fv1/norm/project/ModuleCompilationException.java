package dev.w0fv1.norm.project;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import java.util.List;
import java.util.stream.Collectors;

public final class ModuleCompilationException extends ProjectLoadException {
  private static final long serialVersionUID = 1L;
  private final transient List<Diagnostic> diagnostics;

  ModuleCompilationException(List<Diagnostic> diagnostics) {
    this(diagnostics, null, ProjectInputSnapshot.empty());
  }

  private ModuleCompilationException(
      List<Diagnostic> diagnostics, Throwable cause, ProjectInputSnapshot inputs) {
    super(
        diagnostics.stream()
            .map(DiagnosticRenderer::render)
            .collect(Collectors.joining(System.lineSeparator())),
        cause,
        inputs);
    this.diagnostics = List.copyOf(diagnostics);
  }

  @Override
  ModuleCompilationException withInputs(ProjectInputSnapshot inputs) {
    return new ModuleCompilationException(diagnostics, this, inputs);
  }

  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }
}
