package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.http.PlatformHttpHeader;
import dev.w0fv1.norm.platform.websocket.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

final class JdkWebSocketTransport implements WebSocketTransport {
  private final Supplier<HttpClient> clientFactory;
  private HttpClient client;

  JdkWebSocketTransport(Supplier<HttpClient> clientFactory) {
    this.clientFactory = Objects.requireNonNull(clientFactory);
  }

  @Override
  public PlatformWebSocket connect(
      String uri,
      List<PlatformHttpHeader> headers,
      List<String> subprotocols,
      int maximumMessageBytes,
      OperationControl control) {
    var connection = new JdkWebSocketConnection(uri, maximumMessageBytes);
    java.util.concurrent.CompletableFuture<java.net.http.WebSocket> future = null;
    try {
      connection.check(control, WebSocketOperation.CONNECT);
      if (maximumMessageBytes < 1)
        throw new IllegalArgumentException("maximumMessageBytes must be positive");
      HttpClient transport;
      synchronized (this) {
        if (client == null) client = Objects.requireNonNull(clientFactory.get());
        transport = client;
      }
      var builder =
          transport
              .newWebSocketBuilder()
              .connectTimeout(Duration.ofNanos(control.remainingNanoseconds()));
      headers.forEach(header -> builder.header(header.name(), header.value()));
      if (!subprotocols.isEmpty())
        builder.subprotocols(
            subprotocols.getFirst(),
            subprotocols.subList(1, subprotocols.size()).toArray(String[]::new));
      future = builder.buildAsync(URI.create(uri), connection);
      connection.await(future, control, WebSocketOperation.CONNECT);
      return connection;
    } catch (RuntimeException failure) {
      connection.close();
      if (future != null) future.cancel(true);
      throw connection.failure(WebSocketOperation.CONNECT, failure);
    }
  }
}
