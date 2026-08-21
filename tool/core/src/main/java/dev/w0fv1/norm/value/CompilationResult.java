package dev.w0fv1.norm.value;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompilationResult(Optional<TypedProgram> program, List<Diagnostic> diagnostics) {
  public CompilationResult {
    program = Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean isSuccess() {
    return program.isPresent();
  }
}
