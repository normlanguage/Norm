package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.execution.HttpFailure;
import dev.w0fv1.norm.execution.HttpOperation;
import dev.w0fv1.norm.execution.HttpTransport;
import dev.w0fv1.norm.execution.OperationControl;
import dev.w0fv1.norm.execution.PlatformDuration;
import dev.w0fv1.norm.execution.PlatformHttpException;
import dev.w0fv1.norm.execution.PlatformHttpHeader;
import dev.w0fv1.norm.execution.PlatformHttpRequest;
import dev.w0fv1.norm.execution.PlatformHttpResponse;
import dev.w0fv1.norm.execution.PlatformRead;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLException;

final class JdkHttpTransport implements HttpTransport {
  private static final long CANCELLATION_POLL_MILLIS = 20;
  private final HttpClient client;

  JdkHttpTransport(HttpClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  @Override
  public PlatformHttpResponse send(PlatformHttpRequest request, OperationControl control) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(control, "control");
    if (control.isCancellationRequested()) {
      throw failure(
          HttpOperation.SEND,
          HttpFailure.CANCELLED,
          request.uri(),
          "HTTP request was cancelled",
          new CancellationException());
    }
    try {
      HttpRequest hostRequest = request(request, control.timeout());
      var pending = client.sendAsync(hostRequest, HttpResponse.BodyHandlers.ofInputStream());
      for (; ; ) {
        if (control.isCancellationRequested()) {
          pending.cancel(true);
          throw failure(
              HttpOperation.SEND,
              HttpFailure.CANCELLED,
              request.uri(),
              "HTTP request was cancelled",
              new CancellationException());
        }
        if (control.hasTimedOut()) {
          pending.cancel(true);
          throw failure(
              HttpOperation.SEND,
              HttpFailure.TIMEOUT,
              request.uri(),
              "HTTP request timed out",
              new HttpTimeoutException("request timed out"));
        }
        try {
          HttpResponse<InputStream> response =
              pending.get(
                  Math.min(
                      TimeUnit.MILLISECONDS.toNanos(CANCELLATION_POLL_MILLIS),
                      control.remainingNanoseconds()),
                  TimeUnit.NANOSECONDS);
          return new JdkHttpResponse(request.uri(), response, control);
        } catch (TimeoutException ignored) {
        } catch (InterruptedException failure) {
          pending.cancel(true);
          Thread.currentThread().interrupt();
          throw failure(
              HttpOperation.SEND,
              HttpFailure.CANCELLED,
              request.uri(),
              "HTTP request was interrupted",
              failure);
        }
      }
    } catch (PlatformHttpException failure) {
      throw failure;
    } catch (ExecutionException failure) {
      throw failure(HttpOperation.SEND, request.uri(), failure.getCause());
    } catch (CancellationException failure) {
      throw failure(
          HttpOperation.SEND,
          HttpFailure.CANCELLED,
          request.uri(),
          "HTTP request was cancelled",
          failure);
    } catch (IllegalArgumentException | SecurityException failure) {
      throw failure(
          HttpOperation.SEND,
          HttpFailure.INVALID_REQUEST,
          request.uri(),
          message(HttpFailure.INVALID_REQUEST, failure),
          failure);
    }
  }

  private static HttpRequest request(PlatformHttpRequest request, PlatformDuration timeout) {
    if (timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(request.uri()))
            .timeout(Duration.ofSeconds(timeout.seconds(), timeout.nanoseconds()));
    request.headers().forEach(header -> builder.header(header.name(), header.value()));
    HttpRequest.BodyPublisher body =
        request
            .body()
            .<HttpRequest.BodyPublisher>map(HttpRequest.BodyPublishers::ofByteArray)
            .orElseGet(HttpRequest.BodyPublishers::noBody);
    return builder.method(request.method().name(), body).build();
  }

  private static PlatformHttpException failure(
      HttpOperation operation, String uri, Throwable exception) {
    if (!containsIOException(exception)) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      if (exception instanceof Error error) throw error;
      throw new IllegalStateException("unexpected HTTP platform failure", exception);
    }
    HttpFailure reason = reason(exception);
    return failure(operation, reason, uri, message(reason, exception), exception);
  }

  private static boolean containsIOException(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof IOException) return true;
    }
    return false;
  }

  private static PlatformHttpException failure(
      HttpOperation operation,
      HttpFailure reason,
      String uri,
      String message,
      Throwable exception) {
    return new PlatformHttpException(operation, reason, uri, message, exception);
  }

  private static HttpFailure reason(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof HttpTimeoutException) return HttpFailure.TIMEOUT;
      if (current instanceof SSLException) return HttpFailure.TLS;
      if (current instanceof UnknownHostException || current instanceof ConnectException) {
        return HttpFailure.CONNECT;
      }
      if (current instanceof ProtocolException) return HttpFailure.PROTOCOL;
    }
    return HttpFailure.IO;
  }

  private static String message(HttpFailure reason, Throwable exception) {
    return Objects.requireNonNullElse(exception.getMessage(), reason.name());
  }

  private static final class JdkHttpResponse implements PlatformHttpResponse {
    private static final int PREFERRED_CHUNK_BYTES = 64 * 1024;
    private final String uri;
    private final int statusCode;
    private final List<PlatformHttpHeader> headers;
    private final InputStream body;
    private final OperationControl control;
    private final AtomicBoolean closed = new AtomicBoolean();

    private JdkHttpResponse(
        String uri, HttpResponse<InputStream> response, OperationControl control) {
      this.uri = uri;
      statusCode = response.statusCode();
      List<PlatformHttpHeader> values = new ArrayList<>();
      response
          .headers()
          .map()
          .forEach(
              (name, entries) ->
                  entries.forEach(value -> values.add(new PlatformHttpHeader(name, value))));
      headers = List.copyOf(values);
      body = response.body();
      this.control = control;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public List<PlatformHttpHeader> headers() {
      return headers;
    }

    @Override
    public PlatformRead read(int maximumBytes) {
      if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
      if (closed.get()) {
        throw failure(
            HttpOperation.READ,
            HttpFailure.CLOSED,
            uri,
            "HTTP response body is closed",
            new IOException("stream closed"));
      }
      byte[] storage = new byte[Math.min(maximumBytes, PREFERRED_CHUNK_BYTES)];
      CompletableFuture<Integer> pending = new CompletableFuture<>();
      Thread reader =
          Thread.startVirtualThread(
              () -> {
                try {
                  int length;
                  do {
                    length = body.read(storage);
                  } while (length == 0);
                  pending.complete(length);
                } catch (Throwable failure) {
                  pending.completeExceptionally(failure);
                }
              });
      for (; ; ) {
        if (control.isCancellationRequested()) {
          abort(reader);
          throw failure(
              HttpOperation.READ,
              HttpFailure.CANCELLED,
              uri,
              "HTTP response read was cancelled",
              new CancellationException());
        }
        if (control.hasTimedOut()) {
          abort(reader);
          throw failure(
              HttpOperation.READ,
              HttpFailure.TIMEOUT,
              uri,
              "HTTP response read timed out",
              new HttpTimeoutException("response read timed out"));
        }
        try {
          int length =
              pending.get(
                  Math.min(
                      TimeUnit.MILLISECONDS.toNanos(CANCELLATION_POLL_MILLIS),
                      control.remainingNanoseconds()),
                  TimeUnit.NANOSECONDS);
          return length < 0 ? PlatformRead.Eof.INSTANCE : new PlatformRead.Data(storage, length);
        } catch (TimeoutException ignored) {
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          abort(reader);
          throw failure(
              HttpOperation.READ,
              HttpFailure.CANCELLED,
              uri,
              "HTTP response read was interrupted",
              failure);
        } catch (ExecutionException failure) {
          Throwable cause = failure.getCause();
          if (cause instanceof IOException exception) {
            abort(reader);
            throw failure(HttpOperation.READ, uri, exception);
          }
          if (cause instanceof RuntimeException exception) throw exception;
          if (cause instanceof Error error) throw error;
          throw new IllegalStateException("unexpected HTTP response read failure", cause);
        }
      }
    }

    private void abort(Thread reader) {
      closed.set(true);
      try {
        body.close();
      } catch (IOException ignored) {
      }
      reader.interrupt();
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) return;
      try {
        body.close();
      } catch (IOException exception) {
        throw failure(HttpOperation.CLOSE, uri, exception);
      }
    }
  }
}
