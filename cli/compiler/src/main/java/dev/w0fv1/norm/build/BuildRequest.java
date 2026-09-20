package dev.w0fv1.norm.build;

import java.nio.file.Path;
import java.util.Objects;

public record BuildRequest(
    Path input, ApplicationBuildTarget target, boolean diagnostics, WindowsSubsystem subsystem) {
  public BuildRequest {
    input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
    Objects.requireNonNull(target, "target").validateDiagnostics(diagnostics);
    Objects.requireNonNull(subsystem, "subsystem");
    if (subsystem == WindowsSubsystem.WINDOWED
        && !System.getProperty("os.name", "").startsWith("Windows"))
      throw new IllegalArgumentException("--windowed requires a Windows build");
  }
}
