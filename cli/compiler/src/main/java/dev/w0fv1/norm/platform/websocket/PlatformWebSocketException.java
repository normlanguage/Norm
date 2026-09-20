package dev.w0fv1.norm.platform.websocket;

import java.util.Objects;

public final class PlatformWebSocketException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final WebSocketOperation operation;
  private final WebSocketFailure reason;
  private final String uri;

  public PlatformWebSocketException(
      WebSocketOperation operation,
      WebSocketFailure reason,
      String uri,
      String message,
      Throwable cause) {
    super(Objects.requireNonNull(message), cause);
    this.operation = Objects.requireNonNull(operation);
    this.reason = Objects.requireNonNull(reason);
    this.uri = Objects.requireNonNull(uri);
  }

  public WebSocketOperation operation() {
    return operation;
  }

  public WebSocketFailure reason() {
    return reason;
  }

  public String uri() {
    return uri;
  }
}
