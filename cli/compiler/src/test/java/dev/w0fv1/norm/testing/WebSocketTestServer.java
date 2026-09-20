package dev.w0fv1.norm.testing;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.enums.Opcode;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public final class WebSocketTestServer extends WebSocketServer implements AutoCloseable {
  public final CompletableFuture<WebSocket> peer = new CompletableFuture<>();
  public final CompletableFuture<ClientHandshake> handshake = new CompletableFuture<>();
  public final CompletableFuture<Integer> closed = new CompletableFuture<>();
  public final CompletableFuture<byte[]> pong = new CompletableFuture<>();
  private final CompletableFuture<Void> started = new CompletableFuture<>();

  public WebSocketTestServer() throws Exception {
    this(null);
  }

  public WebSocketTestServer(javax.net.ssl.SSLContext tls) throws Exception {
    super(
        new InetSocketAddress("127.0.0.1", 0),
        1,
        java.util.List.of(
            new org.java_websocket.drafts.Draft_6455(
                java.util.List.of(),
                java.util.List.of(
                    new org.java_websocket.protocols.Protocol("norm.v1"),
                    new org.java_websocket.protocols.Protocol("")))));
    if (tls != null)
      setWebSocketFactory(new org.java_websocket.server.DefaultSSLWebSocketServerFactory(tls));
    setConnectionLostTimeout(0);
    start();
    started.get(10, TimeUnit.SECONDS);
  }

  public String uri() {
    return "ws://127.0.0.1:" + getPort() + "/echo?key=value";
  }

  @Override
  public void onStart() {
    started.complete(null);
  }

  @Override
  public void onOpen(WebSocket socket, ClientHandshake request) {
    peer.complete(socket);
    handshake.complete(request);
  }

  @Override
  public void onMessage(WebSocket socket, String text) {
    if (text.equals("fragment")) {
      socket.sendFragmentedFrame(
          Opcode.TEXT,
          ByteBuffer.wrap("你".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          false);
      socket.sendFragmentedFrame(
          Opcode.TEXT,
          ByteBuffer.wrap("好".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          true);
    } else socket.send(text);
  }

  @Override
  public void onMessage(WebSocket socket, ByteBuffer bytes) {
    socket.send(bytes);
  }

  @Override
  public void onWebsocketPong(WebSocket socket, org.java_websocket.framing.Framedata frame) {
    var data = frame.getPayloadData();
    byte[] copy = new byte[data.remaining()];
    data.get(copy);
    pong.complete(copy);
  }

  @Override
  public void onClose(WebSocket socket, int code, String reason, boolean remote) {
    closed.complete(code);
  }

  @Override
  public void onError(WebSocket socket, Exception error) {
    started.completeExceptionally(error);
  }

  @Override
  public void close() {
    try {
      stop(1000);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(error);
    }
  }
}
