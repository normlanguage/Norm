package dev.w0fv1.norm.project;

import java.util.List;
import java.util.Objects;

public record ProjectLoadResult(
    ProjectSourceSet sources,
    ProjectInputSnapshot inputs,
    List<ModuleEvaluation> moduleEvaluations) {
  public ProjectLoadResult {
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(inputs, "inputs");
    moduleEvaluations = List.copyOf(moduleEvaluations);
  }
}
