package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.websocket.*;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

final class JdkWebSocketConnection implements PlatformWebSocket, WebSocket.Listener {
  private final String uri;
  private final int maximumMessageBytes;
  private final ReentrantLock sending = new ReentrantLock();
  private final ReentrantLock receiving = new ReentrantLock();
  private final CompletableFuture<WebSocketEvent.Closed> peerClosed = new CompletableFuture<>();
  private CompletableFuture<WebSocketEvent> incoming = new CompletableFuture<>();
  private final StringBuilder text = new StringBuilder();
  private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
  private volatile WebSocket socket;
  private volatile boolean disposed;
  private boolean finishing;
  private WebSocketEvent.Closed closed;
  private PlatformWebSocketException terminal;

  JdkWebSocketConnection(String uri, int maximumMessageBytes) {
    this.uri = uri;
    this.maximumMessageBytes = maximumMessageBytes;
  }

  @Override
  public synchronized void onOpen(WebSocket socket) {
    this.socket = socket;
    if (disposed) socket.abort();
    else socket.request(1);
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String subprotocol() {
    return socket.getSubprotocol();
  }

  @Override
  public synchronized CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
    if (finishing) {
      socket.request(1);
      return null;
    }
    if ((long) text.length() + data.length() > maximumMessageBytes) {
      fail(
          new PlatformWebSocketException(
              WebSocketOperation.RECEIVE,
              WebSocketFailure.MESSAGE_TOO_LARGE,
              uri,
              "WebSocket message exceeds maximumMessageBytes",
              null));
      return null;
    }
    text.append(data);
    if (!last) socket.request(1);
    else {
      String complete = text.toString();
      text.setLength(0);
      if (complete.getBytes(StandardCharsets.UTF_8).length > maximumMessageBytes) {
        fail(
            new PlatformWebSocketException(
                WebSocketOperation.RECEIVE,
                WebSocketFailure.MESSAGE_TOO_LARGE,
                uri,
                "WebSocket message exceeds maximumMessageBytes",
                null));
      } else incoming.complete(new WebSocketEvent.Text(complete));
    }
    return null;
  }

  @Override
  public synchronized CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
    if (finishing) {
      socket.request(1);
      return null;
    }
    if ((long) binary.size() + data.remaining() > maximumMessageBytes) {
      fail(
          new PlatformWebSocketException(
              WebSocketOperation.RECEIVE,
              WebSocketFailure.MESSAGE_TOO_LARGE,
              uri,
              "WebSocket message exceeds maximumMessageBytes",
              null));
      return null;
    }
    byte[] chunk = new byte[data.remaining()];
    data.get(chunk);
    binary.writeBytes(chunk);
    if (!last) socket.request(1);
    else {
      incoming.complete(new WebSocketEvent.Binary(binary.toByteArray()));
      binary.reset();
    }
    return null;
  }

  @Override
  public CompletionStage<?> onPing(WebSocket socket, ByteBuffer data) {
    socket.request(1);
    return null;
  }

  @Override
  public synchronized CompletionStage<?> onPong(WebSocket socket, ByteBuffer data) {
    if (finishing) {
      socket.request(1);
      return null;
    }
    byte[] bytes = new byte[data.remaining()];
    data.get(bytes);
    incoming.complete(new WebSocketEvent.Pong(bytes));
    return null;
  }

  @Override
  public synchronized CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
    closed = new WebSocketEvent.Closed(code, reason);
    incoming.complete(closed);
    peerClosed.complete(closed);
    return null;
  }

  @Override
  public synchronized void onError(WebSocket socket, Throwable error) {
    fail(failure(WebSocketOperation.RECEIVE, error));
  }

  private synchronized void fail(PlatformWebSocketException error) {
    if (terminal == null) terminal = error;
    incoming.completeExceptionally(terminal);
    peerClosed.completeExceptionally(terminal);
    if (socket != null) socket.abort();
  }

  @Override
  public void sendText(String value, OperationControl control) {
    if (value.length() > maximumMessageBytes
        || value.getBytes(StandardCharsets.UTF_8).length > maximumMessageBytes) {
      throw new PlatformWebSocketException(
          WebSocketOperation.SEND,
          WebSocketFailure.MESSAGE_TOO_LARGE,
          uri,
          "WebSocket message exceeds maximumMessageBytes",
          null);
    }
    send(() -> socket.sendText(value, true), control, WebSocketOperation.SEND);
  }

  @Override
  public void sendBinary(byte[] data, OperationControl control) {
    if (data.length > maximumMessageBytes)
      throw new PlatformWebSocketException(
          WebSocketOperation.SEND,
          WebSocketFailure.MESSAGE_TOO_LARGE,
          uri,
          "WebSocket message exceeds maximumMessageBytes",
          null);
    send(() -> socket.sendBinary(ByteBuffer.wrap(data), true), control, WebSocketOperation.SEND);
  }

  @Override
  public void ping(byte[] data, OperationControl control) {
    send(() -> socket.sendPing(ByteBuffer.wrap(data)), control, WebSocketOperation.SEND);
  }

  private void send(
      Supplier<CompletableFuture<WebSocket>> action,
      OperationControl control,
      WebSocketOperation operation) {
    boolean acquired = false;
    boolean started = false;
    try {
      while (!acquired) {
        check(control, operation);
        acquired =
            sending.tryLock(
                Math.min(control.remainingNanoseconds(), TimeUnit.MILLISECONDS.toNanos(20)),
                TimeUnit.NANOSECONDS);
      }
      check(control, operation);
      if (disposed)
        throw new PlatformWebSocketException(
            operation, WebSocketFailure.CLOSED, uri, "WebSocket is closed", null);
      var pending = action.get();
      started = true;
      await(pending, control, operation);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      if (started) close();
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.CANCELLED, uri, "WebSocket operation interrupted", error);
    } catch (RuntimeException error) {
      var mapped = failure(operation, error);
      if (started && mapped.reason() != WebSocketFailure.INVALID_REQUEST) close();
      throw mapped;
    } finally {
      if (acquired) sending.unlock();
    }
  }

  @Override
  public WebSocketEvent receive(OperationControl control) {
    if (!receiving.tryLock())
      throw new PlatformWebSocketException(
          WebSocketOperation.RECEIVE,
          WebSocketFailure.BUSY,
          uri,
          "Only one receive operation may be active",
          null);
    try {
      CompletableFuture<WebSocketEvent> pending;
      synchronized (this) {
        if (terminal != null) throw terminal;
        if (closed != null && !incoming.isDone()) return closed;
        pending = incoming;
      }
      WebSocketEvent result = await(pending, control, WebSocketOperation.RECEIVE);
      synchronized (this) {
        incoming = new CompletableFuture<>();
        if (terminal != null) incoming.completeExceptionally(terminal);
        else if (closed != null) incoming.complete(closed);
        else socket.request(1);
      }
      return result;
    } finally {
      receiving.unlock();
    }
  }

  @Override
  public void finish(int code, String reason, OperationControl control) {
    send(() -> socket.sendClose(code, reason), control, WebSocketOperation.CLOSE);
    synchronized (this) {
      finishing = true;
      text.setLength(0);
      binary.reset();
      socket.request(1);
    }
    try {
      await(peerClosed, control, WebSocketOperation.CLOSE);
    } finally {
      close();
    }
  }

  @Override
  public synchronized void close() {
    if (disposed) return;
    disposed = true;
    fail(
        new PlatformWebSocketException(
            WebSocketOperation.RECEIVE, WebSocketFailure.CLOSED, uri, "WebSocket is closed", null));
  }

  void check(OperationControl control, WebSocketOperation operation) {
    if (control.timeout().seconds() < 0
        || control.timeout().seconds() == 0 && control.timeout().nanoseconds() <= 0)
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.INVALID_REQUEST, uri, "timeout must be positive", null);
    if (control.isCancellationRequested() || Thread.currentThread().isInterrupted())
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.CANCELLED, uri, "WebSocket operation cancelled", null);
    if (control.hasTimedOut())
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.TIMEOUT, uri, "WebSocket operation timed out", null);
  }

  <T> T await(
      CompletableFuture<T> pending, OperationControl control, WebSocketOperation operation) {
    try {
      for (; ; ) {
        check(control, operation);
        try {
          return pending.get(
              Math.min(control.remainingNanoseconds(), TimeUnit.MILLISECONDS.toNanos(20)),
              TimeUnit.NANOSECONDS);
        } catch (TimeoutException ignored) {
        }
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.CANCELLED, uri, "WebSocket operation interrupted", error);
    } catch (ExecutionException error) {
      throw failure(operation, error.getCause());
    }
  }

  PlatformWebSocketException failure(WebSocketOperation operation, Throwable error) {
    if (error instanceof PlatformWebSocketException mapped) return mapped;
    WebSocketFailure reason = WebSocketFailure.IO;
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof SSLException) {
        reason = WebSocketFailure.TLS;
        break;
      }
      if (current instanceof WebSocketHandshakeException) {
        reason = WebSocketFailure.HANDSHAKE;
        break;
      }
      if (current instanceof HttpTimeoutException) {
        reason = WebSocketFailure.TIMEOUT;
        break;
      }
      if (current instanceof ConnectException) {
        reason = WebSocketFailure.CONNECT;
        break;
      }
      if (current instanceof ProtocolException) {
        reason = WebSocketFailure.PROTOCOL;
        break;
      }
      if (current instanceof IllegalArgumentException) {
        reason = WebSocketFailure.INVALID_REQUEST;
        break;
      }
      if (current instanceof IllegalStateException) {
        reason = WebSocketFailure.CLOSED;
        break;
      }
    }
    return new PlatformWebSocketException(
        operation,
        reason,
        uri,
        java.util.Objects.requireNonNullElse(error.getMessage(), reason.name()),
        error);
  }
}
