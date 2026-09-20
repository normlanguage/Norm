package dev.w0fv1.norm.platform.websocket;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.http.PlatformHttpHeader;
import java.util.List;

public interface WebSocketTransport {
  PlatformWebSocket connect(
      String uri,
      List<PlatformHttpHeader> headers,
      List<String> subprotocols,
      int maximumMessageBytes,
      OperationControl control);
}
