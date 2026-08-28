package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.execution.HttpFailure;
import dev.w0fv1.norm.execution.HttpMethod;
import dev.w0fv1.norm.execution.HttpOperation;
import dev.w0fv1.norm.execution.OperationControl;
import dev.w0fv1.norm.execution.PlatformDuration;
import dev.w0fv1.norm.execution.PlatformHttpException;
import dev.w0fv1.norm.execution.PlatformHttpHeader;
import dev.w0fv1.norm.execution.PlatformHttpRequest;
import dev.w0fv1.norm.execution.PlatformRead;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class JdkHttpTransportTest {
  private HttpServer server;
  private ExecutorService executor;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
    if (executor != null) executor.close();
  }

  @Test
  void sendsARequestAndStreamsTheRealLoopbackResponse() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/resource",
        exchange -> {
          assertEquals("POST", exchange.getRequestMethod());
          assertEquals("request", exchange.getRequestHeaders().getFirst("X-Norm"));
          assertEquals(
              "payload",
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.getResponseHeaders().add("X-Value", "first");
          exchange.getResponseHeaders().add("X-Value", "second");
          byte[] response = "response".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(201, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();

    var transport = JdkSystemPlatform.standard().httpTransport();
    var request =
        new PlatformHttpRequest(
            HttpMethod.POST,
            uri("/resource"),
            List.of(new PlatformHttpHeader("X-Norm", "request")),
            Optional.of("payload".getBytes(StandardCharsets.UTF_8)));

    try (var response =
        transport.send(request, new OperationControl(() -> false, new PlatformDuration(5, 0)))) {
      assertEquals(201, response.statusCode());
      assertEquals(
          List.of("first", "second"),
          response.headers().stream()
              .filter(header -> header.name().equalsIgnoreCase("X-Value"))
              .map(PlatformHttpHeader::value)
              .toList());
      PlatformRead.Data first = (PlatformRead.Data) response.read(3);
      PlatformRead.Data second = (PlatformRead.Data) response.read(32);
      assertEquals("res", new String(first.storage(), 0, first.length(), StandardCharsets.UTF_8));
      assertEquals(
          "ponse", new String(second.storage(), 0, second.length(), StandardCharsets.UTF_8));
      assertEquals(PlatformRead.Eof.INSTANCE, response.read(1));
    }
  }

  @Test
  void normalizesTimeoutCancellationAndInvalidRequests() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(500);
            exchange.sendResponseHeaders(204, -1);
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.createContext(
        "/slow-body",
        exchange -> {
          try {
            exchange.sendResponseHeaders(200, 1);
            exchange.getResponseBody().flush();
            Thread.sleep(2_000);
            exchange.getResponseBody().write(1);
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    var transport = JdkSystemPlatform.standard().httpTransport();

    PlatformHttpException timeout =
        assertThrows(
            PlatformHttpException.class,
            () ->
                transport.send(
                    new PlatformHttpRequest(
                        HttpMethod.GET, uri("/slow"), List.of(), Optional.empty()),
                    new OperationControl(() -> false, new PlatformDuration(0, 20_000_000))));
    PlatformHttpException cancelled =
        assertThrows(
            PlatformHttpException.class,
            () ->
                transport.send(
                    new PlatformHttpRequest(
                        HttpMethod.GET, uri("/slow"), List.of(), Optional.empty()),
                    new OperationControl(() -> true, new PlatformDuration(5, 0))));
    PlatformHttpException invalid =
        assertThrows(
            PlatformHttpException.class,
            () ->
                transport.send(
                    new PlatformHttpRequest(
                        HttpMethod.GET, "not a URI", List.of(), Optional.empty()),
                    new OperationControl(() -> false, new PlatformDuration(5, 0))));
    var slowBody =
        transport.send(
            new PlatformHttpRequest(HttpMethod.GET, uri("/slow-body"), List.of(), Optional.empty()),
            new OperationControl(() -> false, new PlatformDuration(1, 0)));
    PlatformHttpException bodyTimeout =
        assertThrows(PlatformHttpException.class, () -> slowBody.read(1));

    assertEquals(HttpOperation.SEND, timeout.operation());
    assertEquals(HttpFailure.TIMEOUT, timeout.reason());
    assertEquals(HttpFailure.CANCELLED, cancelled.reason());
    assertEquals(HttpFailure.INVALID_REQUEST, invalid.reason());
    assertEquals(HttpOperation.READ, bodyTimeout.operation());
    assertEquals(HttpFailure.TIMEOUT, bodyTimeout.reason());
  }

  private String uri(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }
}
