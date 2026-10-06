package dev.w0fv1.norm.project;

import java.io.IOException;
import java.util.Objects;

public sealed class ProjectLoadException extends IOException permits ModuleCompilationException {
  @java.io.Serial private static final long serialVersionUID = 1L;
  private final ProjectInputSnapshot inputs;

  ProjectLoadException(Exception cause, ProjectInputSnapshot inputs) {
    this(cause.getMessage(), cause, inputs);
  }

  protected ProjectLoadException(String message, Throwable cause, ProjectInputSnapshot inputs) {
    super(message, cause);
    this.inputs = Objects.requireNonNull(inputs, "inputs");
  }

  ProjectLoadException withInputs(ProjectInputSnapshot inputs) {
    return new ProjectLoadException(this, inputs);
  }

  public ProjectInputSnapshot inputs() {
    return inputs;
  }
}
