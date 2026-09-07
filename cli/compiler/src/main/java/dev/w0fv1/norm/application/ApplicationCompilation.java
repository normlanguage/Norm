package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import java.util.Objects;
import java.util.Optional;

public record ApplicationCompilation(
    CompilationResult result, Optional<CompiledApplication> application) implements AutoCloseable {
  public ApplicationCompilation {
    Objects.requireNonNull(result, "result");
    application = Objects.requireNonNull(application, "application");
    if (result.isSuccess() != application.isPresent()) {
      throw new IllegalArgumentException("successful compilation requires a prepared application");
    }
  }

  @Override
  public void close() {
    application.ifPresent(CompiledApplication::close);
  }
}
