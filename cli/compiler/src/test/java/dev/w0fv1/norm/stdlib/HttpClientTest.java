package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.PlatformRead;
import dev.w0fv1.norm.platform.SystemPlatform;
import dev.w0fv1.norm.platform.file.FileSystem;
import dev.w0fv1.norm.platform.http.HttpFailure;
import dev.w0fv1.norm.platform.http.HttpOperation;
import dev.w0fv1.norm.platform.http.HttpTransport;
import dev.w0fv1.norm.platform.http.PlatformHttpException;
import dev.w0fv1.norm.platform.http.PlatformHttpHeader;
import dev.w0fv1.norm.platform.http.PlatformHttpRequest;
import dev.w0fv1.norm.platform.http.PlatformHttpResponse;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.platform.time.SystemClock;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class HttpClientTest {
  @Test
  void sendsAndDecodesJsonAgainstARealLoopbackServer() throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(10_000);
      Future<?> exchange =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  var input = socket.getInputStream();
                  ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                  int matched = 0;
                  while (matched < 4) {
                    int value = input.read();
                    if (value < 0) throw new java.io.EOFException();
                    headerBytes.write(value);
                    matched =
                        switch (matched) {
                          case 0 -> value == '\r' ? 1 : 0;
                          case 1 -> value == '\n' ? 2 : 0;
                          case 2 -> value == '\r' ? 3 : 0;
                          default -> value == '\n' ? 4 : 0;
                        };
                  }
                  String headers = headerBytes.toString(StandardCharsets.US_ASCII);
                  String[] lines = headers.split("\\r\\n");
                  org.junit.jupiter.api.Assertions.assertEquals(
                      "POST /messages HTTP/1.1", lines[0]);
                  int contentLength = 0;
                  boolean jsonContentType = false;
                  for (int index = 1; index < lines.length; index++) {
                    String line = lines[index];
                    if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                      contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                    if (line.equalsIgnoreCase("Content-Type: application/json")) {
                      jsonContentType = true;
                    }
                  }
                  org.junit.jupiter.api.Assertions.assertTrue(jsonContentType);
                  byte[] body = input.readNBytes(contentLength);
                  org.junit.jupiter.api.Assertions.assertEquals(
                      "{\"message\":\"你好\"}", new String(body, StandardCharsets.UTF_8));
                  byte[] response = "{\"message\":\"pong\"}".getBytes(StandardCharsets.UTF_8);
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 200 OK\r\n"
                                  + "Content-Length: "
                                  + response.length
                                  + "\r\nContent-Type: application/json\r\n"
                                  + "Connection: close\r\n\r\n")
                              .getBytes(StandardCharsets.US_ASCII));
                  socket.getOutputStream().write(response);
                } catch (java.io.IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              });

      assertOutput(
          JdkSystemPlatform.standard(),
          "import std.http.HttpRequest import std.http.HttpResponse import std.http.Uri "
              + "import std.http.decodeJson import std.http.postJson import std.http.systemHttpClient "
              + "import std.io.Bytes import std.io.readAll import std.io.use "
              + "import std.serialization.SerialName import std.serialization.Serializable "
              + "import std.time.Duration import std.time.duration @Serializable() value Message { "
              + "@SerialName(name: \"message\") String text } Void main() { "
              + "HttpRequest request = postJson(uri: Uri(value: \"http://127.0.0.1:"
              + server.getLocalPort()
              + "/messages\"), body: Message(text: \"你好\")) "
              + "HttpResponse response = systemHttpClient().send(request: request, "
              + "timeout: duration(seconds: 5, nanoseconds: 0)) "
              + "Bytes body = use<Bytes>(resource: response, body: () { "
              + "printLine(response.status().code) readAll(reader: response, maximumBytes: 128) }) "
              + "Message decoded = decodeJson<Message>(body: body) printLine(decoded.text) }",
          "200",
          "pong");
      exchange.get();
    }
  }

  @Test
  void performsAUserLevelRequestAgainstARealLoopbackServer() throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Future<?> exchange =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  BufferedReader input =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.US_ASCII));
                  org.junit.jupiter.api.Assertions.assertEquals(
                      "GET /health HTTP/1.1", input.readLine());
                  for (String line = input.readLine(); line != null && !line.isEmpty(); ) {
                    line = input.readLine();
                  }
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 202 Accepted\r\n"
                                  + "Content-Length: 4\r\n"
                                  + "Content-Type: text/plain; charset=utf-8\r\n"
                                  + "Connection: close\r\n\r\n"
                                  + "pong")
                              .getBytes(StandardCharsets.US_ASCII));
                } catch (java.io.IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              });

      assertOutput(
          JdkSystemPlatform.standard(),
          "import std.http.HttpRequest import std.http.HttpResponse import std.http.Uri "
              + "import std.http.get import std.http.systemHttpClient "
              + "import std.io.Bytes import std.io.TextEncoding import std.io.decodeText "
              + "import std.io.readAll import std.io.use import std.time.Duration "
              + "import std.time.duration Void main() { HttpRequest request = get(uri: Uri(value: \""
              + "http://127.0.0.1:"
              + server.getLocalPort()
              + "/health\")) HttpResponse response = systemHttpClient().send(request: request, "
              + "timeout: duration(seconds: 5, nanoseconds: 0)) "
              + "String body = use<String>(resource: response, body: () { "
              + "printLine(response.status().code) decodeText(content: readAll("
              + "reader: response, maximumBytes: 4), encoding: TextEncoding.Utf8) }) "
              + "printLine(body) }",
          "202",
          "pong");
      exchange.get();
    }
  }

  @Test
  void sendsTypedRequestsAndStreamsResponses() {
    AtomicBoolean closed = new AtomicBoolean();
    HttpTransport transport =
        (request, control) -> {
          assertRequest(request, control);
          return response(closed);
        };

    assertOutput(
        platform(transport),
        "import std.http.HeaderMap import std.http.HttpHeader import std.http.HttpMethod "
            + "import std.http.HttpRequest import std.http.HttpResponse import std.http.Uri "
            + "import std.http.request import std.http.systemHttpClient "
            + "import std.io.Bytes import std.io.bytes import std.io.readAll import std.io.use "
            + "import std.time.Duration import std.time.duration Void main() { "
            + "HttpRequest requestValue = request(method: HttpMethod.Post, "
            + "uri: Uri(value: \"https://example.test/resource\"), "
            + "headers: HeaderMap(values: [HttpHeader(name: \"X-Norm\", value: \"request\")]), "
            + "body: bytes(values: [1, 2, 3])) "
            + "HttpResponse response = systemHttpClient().send("
            + "request: requestValue, timeout: duration(seconds: 5, nanoseconds: 7)) "
            + "Bytes content = use<Bytes>(resource: response, body: () { "
            + "printLine(response.status().code) "
            + "printLine(response.headers().values[0].name) "
            + "printLine(response.headers().values[0].value) "
            + "readAll(reader: response, maximumBytes: 8) }) "
            + "printLine(content.size()) printLine(content.at(index: 0)) }",
        "201",
        "x-value",
        "first",
        "3",
        "4");

    org.junit.jupiter.api.Assertions.assertTrue(closed.get());
  }

  @Test
  void closesUnconsumedResponsesAtTheExecutionBoundary() {
    AtomicBoolean closed = new AtomicBoolean();
    HttpTransport transport = (request, control) -> response(closed);

    assertOutput(
        platform(transport),
        "import std.http.HttpRequest import std.http.HttpResponse import std.http.Uri "
            + "import std.http.get import std.http.systemHttpClient import std.time.Duration "
            + "import std.time.duration Void main() { HttpRequest request = get("
            + "uri: Uri(value: \"https://example.test/resource\")) "
            + "HttpResponse response = systemHttpClient().send(request: request, "
            + "timeout: duration(seconds: 5, nanoseconds: 0)) "
            + "printLine(response.status().code) }",
        "201");

    org.junit.jupiter.api.Assertions.assertTrue(closed.get());
  }

  @Test
  void exposesTransportFailuresAsCatchableHttpExceptions() {
    HttpTransport transport =
        (request, control) -> {
          throw new PlatformHttpException(
              HttpOperation.SEND,
              HttpFailure.TIMEOUT,
              request.uri(),
              "request timed out",
              new IllegalStateException("timeout"));
        };

    assertOutput(
        platform(transport),
        "import std.http.HttpException import std.http.HttpRequest import std.http.Uri import std.http.get "
            + "import std.http.systemHttpClient import std.time.Duration import std.time.duration Void main() { "
            + "try { systemHttpClient().send(request: get(uri: Uri(value: \"https://example.test\")), "
            + "timeout: duration(seconds: 1, nanoseconds: 0)) } "
            + "catch HttpException error { printLine(error.code) printLine(error.operation) "
            + "printLine(error.reason) printLine(error.uri.value) } }",
        "NORM-HTTP-TIMEOUT",
        "HttpOperation.Send",
        "HttpFailure.Timeout",
        "https://example.test");
  }

  @Test
  void hidesHttpIntrinsicsFromApplications() {
    assertFalse(compile("Void main() { __httpClose(response: null) }").isSuccess());
  }

  private static void assertRequest(PlatformHttpRequest request, OperationControl control) {
    org.junit.jupiter.api.Assertions.assertEquals("POST", request.method().name());
    org.junit.jupiter.api.Assertions.assertEquals("https://example.test/resource", request.uri());
    org.junit.jupiter.api.Assertions.assertEquals(
        List.of(new PlatformHttpHeader("X-Norm", "request")), request.headers());
    assertArrayEquals(new byte[] {1, 2, 3}, request.body().orElseThrow());
    org.junit.jupiter.api.Assertions.assertEquals(5, control.timeout().seconds());
    org.junit.jupiter.api.Assertions.assertEquals(7, control.timeout().nanoseconds());
  }

  private static PlatformHttpResponse response(AtomicBoolean closed) {
    return new PlatformHttpResponse() {
      private final byte[] content = {4, 5, 6};
      private int offset;

      @Override
      public int statusCode() {
        return 201;
      }

      @Override
      public List<PlatformHttpHeader> headers() {
        return List.of(new PlatformHttpHeader("x-value", "first"));
      }

      @Override
      public PlatformRead read(int maximumBytes) {
        if (offset == content.length) return PlatformRead.Eof.INSTANCE;
        int length = Math.min(maximumBytes, content.length - offset);
        byte[] chunk = java.util.Arrays.copyOfRange(content, offset, offset + length);
        offset += length;
        return new PlatformRead.Data(chunk, length);
      }

      @Override
      public void close() {
        closed.set(true);
      }
    };
  }

  private static SystemPlatform platform(HttpTransport transport) {
    JdkSystemPlatform standard = JdkSystemPlatform.standard();
    return new SystemPlatform() {
      @Override
      public FileSystem fileSystem() {
        return standard.fileSystem();
      }

      @Override
      public SystemClock clock() {
        return standard.clock();
      }

      @Override
      public HttpTransport httpTransport() {
        return transport;
      }
    };
  }
}
