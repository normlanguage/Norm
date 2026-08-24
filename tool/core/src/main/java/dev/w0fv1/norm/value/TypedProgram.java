package dev.w0fv1.norm.value;

import dev.w0fv1.norm.core.CoreCompilation;
import java.util.Objects;

public record TypedProgram(CoreCompilation coreCompilation) {
  public TypedProgram {
    Objects.requireNonNull(coreCompilation, "coreCompilation");
  }
}
