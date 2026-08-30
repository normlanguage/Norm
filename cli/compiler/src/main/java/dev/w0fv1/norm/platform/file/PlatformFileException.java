package dev.w0fv1.norm.platform.file;

import java.util.Objects;

public final class PlatformFileException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final FileOperation operation;
  private final FileFailure reason;
  private final String path;

  public PlatformFileException(
      FileOperation operation, FileFailure reason, String path, String message, Throwable cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    this.operation = Objects.requireNonNull(operation, "operation");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.path = Objects.requireNonNull(path, "path");
  }

  public FileOperation operation() {
    return operation;
  }

  public FileFailure reason() {
    return reason;
  }

  public String path() {
    return path;
  }
}
