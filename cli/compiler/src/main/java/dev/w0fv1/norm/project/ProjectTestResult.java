package dev.w0fv1.norm.project;

import dev.w0fv1.norm.value.CompilationResult;
import java.util.Objects;
import java.util.Optional;

public record ProjectTestResult(CompilationResult compilation, Optional<ProjectTestReport> report) {
  public ProjectTestResult {
    Objects.requireNonNull(compilation, "compilation");
    report = Objects.requireNonNull(report, "report");
    if (compilation.isSuccess() != report.isPresent()) {
      throw new IllegalArgumentException("successful test compilation must have a test report");
    }
  }

  public boolean isSuccess() {
    return compilation.isSuccess() && report.orElseThrow().isSuccess();
  }
}
