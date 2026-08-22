package dev.w0fv1.norm.value;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AnalysisResult(
    SemanticModel semanticModel,
    Optional<Syntax.FunctionDecl> entryPoint,
    Optional<BoundProgram> boundProgram,
    List<dev.w0fv1.norm.diagnostic.Diagnostic> diagnostics) {
  public AnalysisResult {
    Objects.requireNonNull(semanticModel, "semanticModel");
    entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
    boundProgram = Objects.requireNonNull(boundProgram, "boundProgram");
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean hasErrors() {
    return diagnostics.stream()
        .anyMatch(
            diagnostic ->
                diagnostic.severity() == dev.w0fv1.norm.diagnostic.DiagnosticSeverity.ERROR);
  }
}
