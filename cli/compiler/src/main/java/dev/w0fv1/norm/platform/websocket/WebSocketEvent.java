package dev.w0fv1.norm.platform.websocket;

public sealed interface WebSocketEvent {
  record Text(String text) implements WebSocketEvent {}

  record Binary(byte[] data) implements WebSocketEvent {}

  record Pong(byte[] data) implements WebSocketEvent {}

  record Closed(int code, String reason) implements WebSocketEvent {}
}
