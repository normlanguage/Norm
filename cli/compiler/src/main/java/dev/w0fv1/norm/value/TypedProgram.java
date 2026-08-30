package dev.w0fv1.norm.value;

import dev.w0fv1.norm.core.CompilationOutput;
import java.util.Objects;

public record TypedProgram(CompilationOutput compilation) {
  public TypedProgram {
    Objects.requireNonNull(compilation, "compilation");
  }
}
