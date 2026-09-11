package dev.w0fv1.norm.build;

import java.nio.file.Path;
import java.util.Objects;

public record BuildRequest(Path input, ApplicationBuildTarget target, boolean diagnostics) {
  public BuildRequest {
    input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
    Objects.requireNonNull(target, "target").validateDiagnostics(diagnostics);
  }
}
