package dev.w0fv1.norm.project;

import java.io.IOException;
import java.util.Objects;

public final class ProjectLoadException extends IOException {
  @java.io.Serial private static final long serialVersionUID = 1L;
  private final ProjectInputSnapshot inputs;

  ProjectLoadException(Exception cause, ProjectInputSnapshot inputs) {
    super(cause.getMessage(), cause);
    this.inputs = Objects.requireNonNull(inputs, "inputs");
  }

  public ProjectInputSnapshot inputs() {
    return inputs;
  }
}
