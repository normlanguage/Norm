package dev.w0fv1.norm.platform.http;

import java.util.Objects;

public final class PlatformHttpException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final HttpOperation operation;
  private final HttpFailure reason;
  private final String uri;

  public PlatformHttpException(
      HttpOperation operation, HttpFailure reason, String uri, String message, Throwable cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    this.operation = Objects.requireNonNull(operation, "operation");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.uri = Objects.requireNonNull(uri, "uri");
  }

  public HttpOperation operation() {
    return operation;
  }

  public HttpFailure reason() {
    return reason;
  }

  public String uri() {
    return uri;
  }
}
