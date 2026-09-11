package dev.w0fv1.norm.build;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public sealed interface BuildResult {
  record Success(Path output) implements BuildResult {
    public Success {
      output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
    }
  }

  record CompilationFailure(List<Diagnostic> diagnostics) implements BuildResult {
    public CompilationFailure {
      diagnostics = List.copyOf(diagnostics);
    }
  }
}
