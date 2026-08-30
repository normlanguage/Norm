package dev.w0fv1.norm.platform.http;

public enum HttpFailure {
  INVALID_REQUEST,
  CONNECT,
  TIMEOUT,
  CANCELLED,
  TLS,
  PROTOCOL,
  CLOSED,
  IO
}
