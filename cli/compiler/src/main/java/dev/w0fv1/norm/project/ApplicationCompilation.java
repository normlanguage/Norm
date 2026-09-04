package dev.w0fv1.norm.project;

import dev.w0fv1.norm.value.CompilationResult;
import java.util.Objects;

public record ApplicationCompilation(ProjectSourceSet sourceSet, CompilationResult result) {
  public ApplicationCompilation {
    Objects.requireNonNull(sourceSet, "sourceSet");
    Objects.requireNonNull(result, "result");
  }
}
