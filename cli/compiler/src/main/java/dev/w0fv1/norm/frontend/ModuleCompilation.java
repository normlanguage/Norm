package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CompilationResult;
import java.util.Objects;
import java.util.Optional;

public record ModuleCompilation(CompilationResult compilation, Optional<CompiledModule> module) {
  public ModuleCompilation {
    Objects.requireNonNull(compilation, "compilation");
    Objects.requireNonNull(module, "module");
    if (compilation.isSuccess() != module.isPresent())
      throw new IllegalArgumentException("compiled module must match compilation success");
  }
}
