package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.PlatformDuration;
import dev.w0fv1.norm.platform.http.PlatformHttpHeader;
import dev.w0fv1.norm.platform.websocket.*;
import dev.w0fv1.norm.testing.WebSocketTestServer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
final class JdkWebSocketTest {
  @Test
  void answersPeerPingAndAggregatesBinaryFragments() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(server.uri(), List.of(), List.of(), 4, budget())) {
      var peer = server.peer.get(5, TimeUnit.SECONDS);
      peer.sendPing();
      assertNotNull(server.pong.get(5, TimeUnit.SECONDS));
      peer.sendFragmentedFrame(
          org.java_websocket.enums.Opcode.BINARY,
          java.nio.ByteBuffer.wrap(new byte[] {1, 2}),
          false);
      peer.sendFragmentedFrame(
          org.java_websocket.enums.Opcode.BINARY,
          java.nio.ByteBuffer.wrap(new byte[] {3, 4}),
          true);
      assertArrayEquals(
          new byte[] {1, 2, 3, 4}, ((WebSocketEvent.Binary) connection.receive(budget())).data());
      assertEquals(
          WebSocketFailure.INVALID_REQUEST,
          assertThrows(
                  PlatformWebSocketException.class, () -> connection.finish(1006, "", budget()))
              .reason());
      connection.sendText("ok", budget());
      assertEquals(new WebSocketEvent.Text("ok"), connection.receive(budget()));
      peer.sendFragmentedFrame(
          org.java_websocket.enums.Opcode.BINARY,
          java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3}),
          false);
      peer.sendFragmentedFrame(
          org.java_websocket.enums.Opcode.BINARY,
          java.nio.ByteBuffer.wrap(new byte[] {4, 5}),
          true);
      assertEquals(
          WebSocketFailure.MESSAGE_TOO_LARGE,
          assertThrows(PlatformWebSocketException.class, () -> connection.receive(budget()))
              .reason());
    }
  }

  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

  @Test
  void usesTlsCertificateValidationAndNegotiatesSubprotocol() throws Exception {
    var keyStorePath = directory.resolve("server.p12");
    var keytool =
        java.nio.file.Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
    var process =
        new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias",
                "test",
                "-keyalg",
                "RSA",
                "-storetype",
                "PKCS12",
                "-keystore",
                keyStorePath.toString(),
                "-storepass",
                "test-password",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=ip:127.0.0.1",
                "-validity",
                "2")
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve("keytool.log").toFile())
            .start();
    assertTrue(process.waitFor(10, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    var keys = java.security.KeyStore.getInstance("PKCS12");
    try (var input = java.nio.file.Files.newInputStream(keyStorePath)) {
      keys.load(input, "test-password".toCharArray());
    }
    var km =
        javax.net.ssl.KeyManagerFactory.getInstance(
            javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
    km.init(keys, "test-password".toCharArray());
    var tm =
        javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
    tm.init(keys);
    var tls = javax.net.ssl.SSLContext.getInstance("TLS");
    tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
    try (var server = new WebSocketTestServer(tls)) {
      String uri = server.uri().replace("ws:", "wss:");
      assertEquals(
          WebSocketFailure.TLS,
          assertThrows(
                  PlatformWebSocketException.class,
                  () ->
                      JdkSystemPlatform.standard()
                          .webSocketTransport()
                          .connect(uri, List.of(), List.of(), 1024, budget()))
              .reason());
      try (var host = java.net.http.HttpClient.newBuilder().sslContext(tls).build();
          var socket =
              JdkSystemPlatform.builder()
                  .httpClient(host)
                  .build()
                  .webSocketTransport()
                  .connect(uri, List.of(), List.of("norm.v1"), 1024, budget())) {
        assertEquals("norm.v1", socket.subprotocol());
        socket.sendText("secure", budget());
        assertEquals(new WebSocketEvent.Text("secure"), socket.receive(budget()));
      }
    }
  }

  @Test
  void preservesBurstOrderWithOneBufferedMessageAndSerializesConcurrentSends() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(server.uri(), List.of(), List.of(), 1024, budget());
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var peer = server.peer.get(5, TimeUnit.SECONDS);
      for (int i = 0; i < 100; i++) peer.send("burst-" + i);
      for (int i = 0; i < 100; i++)
        assertEquals(new WebSocketEvent.Text("burst-" + i), connection.receive(budget()));
      var pending = new java.util.ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 20; i++) {
        String text = "send-" + i;
        pending.add(executor.submit(() -> connection.sendText(text, budget())));
      }
      var messages = new java.util.HashSet<String>();
      for (int i = 0; i < 20; i++)
        messages.add(((WebSocketEvent.Text) connection.receive(budget())).text());
      for (var send : pending) send.get(5, TimeUnit.SECONDS);
      assertEquals(20, messages.size());
      assertEquals(
          WebSocketFailure.INVALID_REQUEST,
          assertThrows(
                  PlatformWebSocketException.class, () -> connection.ping(new byte[126], budget()))
              .reason());
      connection.sendText("still open", budget());
      assertEquals(new WebSocketEvent.Text("still open"), connection.receive(budget()));
    }
  }

  @Test
  void rejectsHandshakeAndTimesOutSilentHandshake() throws Exception {
    var http =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(403, -1);
          exchange.close();
        });
    http.start();
    try {
      assertEquals(
          WebSocketFailure.HANDSHAKE,
          assertThrows(
                  PlatformWebSocketException.class,
                  () ->
                      JdkSystemPlatform.standard()
                          .webSocketTransport()
                          .connect(
                              "ws://127.0.0.1:" + http.getAddress().getPort(),
                              List.of(),
                              List.of(),
                              1024,
                              budget()))
              .reason());
    } finally {
      http.stop(0);
    }
    try (var silent = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      assertEquals(
          WebSocketFailure.TIMEOUT,
          assertThrows(
                  PlatformWebSocketException.class,
                  () ->
                      JdkSystemPlatform.standard()
                          .webSocketTransport()
                          .connect(
                              "ws://127.0.0.1:" + silent.getLocalPort(),
                              List.of(),
                              List.of(),
                              1024,
                              new OperationControl(
                                  () -> false, new PlatformDuration(0, 100_000_000))))
              .reason());
    }
  }

  private static OperationControl budget() {
    return new OperationControl(() -> false, new PlatformDuration(5, 0));
  }

  @Test
  void exchangesTextBinaryFragmentsAndCloseOverRealSockets() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(
                    server.uri(),
                    List.of(new PlatformHttpHeader("X-Norm", "yes")),
                    List.of(),
                    1024,
                    budget())) {
      assertEquals("yes", server.handshake.get(5, TimeUnit.SECONDS).getFieldValue("X-Norm"));
      assertEquals("/echo?key=value", server.handshake.join().getResourceDescriptor());
      connection.sendText("hello", budget());
      assertEquals(new WebSocketEvent.Text("hello"), connection.receive(budget()));
      connection.sendBinary(new byte[] {0, 1, -1}, budget());
      assertArrayEquals(
          new byte[] {0, 1, -1}, ((WebSocketEvent.Binary) connection.receive(budget())).data());
      connection.sendText("fragment", budget());
      assertEquals(new WebSocketEvent.Text("你好"), connection.receive(budget()));
      connection.ping(new byte[] {42}, budget());
      assertArrayEquals(
          new byte[] {42}, ((WebSocketEvent.Pong) connection.receive(budget())).data());
      connection.finish(1000, "done", budget());
      assertEquals(1000, server.closed.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void receiveTimeoutDoesNotConsumeTheNextMessage() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(server.uri(), List.of(), List.of(), 1024, budget())) {
      var error =
          assertThrows(
              PlatformWebSocketException.class,
              () ->
                  connection.receive(
                      new OperationControl(() -> false, new PlatformDuration(0, 30_000_000))));
      assertEquals(WebSocketFailure.TIMEOUT, error.reason());
      connection.sendText("after timeout", budget());
      assertEquals(new WebSocketEvent.Text("after timeout"), connection.receive(budget()));
      server.peer.join().close(1000, "bye");
      assertEquals(new WebSocketEvent.Closed(1000, "bye"), connection.receive(budget()));
    }
  }

  @Test
  void rejectsOversizedMessagesAndInvalidRequests() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(server.uri(), List.of(), List.of(), 4, budget())) {
      server.peer.get(5, TimeUnit.SECONDS).send("12345");
      assertEquals(
          WebSocketFailure.MESSAGE_TOO_LARGE,
          assertThrows(PlatformWebSocketException.class, () -> connection.receive(budget()))
              .reason());
    }
    assertEquals(
        WebSocketFailure.INVALID_REQUEST,
        assertThrows(
                PlatformWebSocketException.class,
                () ->
                    JdkSystemPlatform.standard()
                        .webSocketTransport()
                        .connect("http://localhost", List.of(), List.of(), 1024, budget()))
            .reason());
  }

  @Test
  void cancellationAndCloseUnblockReceivers() throws Exception {
    try (var server = new WebSocketTestServer();
        var connection =
            JdkSystemPlatform.standard()
                .webSocketTransport()
                .connect(server.uri(), List.of(), List.of(), 1024, budget());
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var cancelled = new AtomicBoolean();
      var pending =
          executor.submit(
              () ->
                  assertThrows(
                      PlatformWebSocketException.class,
                      () ->
                          connection.receive(
                              new OperationControl(cancelled::get, new PlatformDuration(5, 0)))));
      cancelled.set(true);
      assertEquals(WebSocketFailure.CANCELLED, pending.get(5, TimeUnit.SECONDS).reason());
      var receiving =
          executor.submit(
              () ->
                  assertThrows(
                      PlatformWebSocketException.class, () -> connection.receive(budget())));
      connection.close();
      assertEquals(WebSocketFailure.CLOSED, receiving.get(5, TimeUnit.SECONDS).reason());
    }
  }
}
