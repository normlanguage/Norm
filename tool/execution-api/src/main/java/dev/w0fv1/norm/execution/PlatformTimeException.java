package dev.w0fv1.norm.execution;

import java.util.Objects;

public final class PlatformTimeException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final TimeOperation operation;
  private final TimeFailure reason;

  public PlatformTimeException(
      TimeOperation operation, TimeFailure reason, String message, Throwable cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    this.operation = Objects.requireNonNull(operation, "operation");
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public TimeOperation operation() {
    return operation;
  }

  public TimeFailure reason() {
    return reason;
  }
}
