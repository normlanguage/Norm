package dev.w0fv1.norm.platform.websocket;

import dev.w0fv1.norm.platform.OperationControl;

public interface PlatformWebSocket extends AutoCloseable {
  String uri();

  String subprotocol();

  void sendText(String text, OperationControl control);

  void sendBinary(byte[] data, OperationControl control);

  void ping(byte[] data, OperationControl control);

  WebSocketEvent receive(OperationControl control);

  void finish(int code, String reason, OperationControl control);

  @Override
  void close();
}
