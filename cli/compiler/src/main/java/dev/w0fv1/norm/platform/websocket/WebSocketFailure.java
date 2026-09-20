package dev.w0fv1.norm.platform.websocket;

public enum WebSocketFailure {
  INVALID_REQUEST,
  CONNECT,
  TLS,
  HANDSHAKE,
  PROTOCOL,
  TIMEOUT,
  CANCELLED,
  CLOSED,
  BUSY,
  MESSAGE_TOO_LARGE,
  IO
}
