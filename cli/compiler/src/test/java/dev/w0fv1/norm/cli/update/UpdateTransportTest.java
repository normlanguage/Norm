package dev.w0fv1.norm.cli.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UpdateTransportTest {
  @TempDir Path directory;

  @Test
  void streamsKnownAndUnknownLengthDownloadsAndRejectsHttpErrors() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] bytes = "test update payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    server.createContext(
        "/known",
        exchange -> {
          exchange.sendResponseHeaders(200, bytes.length);
          try (var body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.createContext(
        "/unknown",
        exchange -> {
          exchange.sendResponseHeaders(200, 0);
          try (var body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.createContext(
        "/missing",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    try (var transport = new UpdateTransport(Duration.ofSeconds(2));
        var progress = new UpdateProgress(new PrintWriter(new StringWriter()), false)) {
      for (String endpoint : java.util.List.of("known", "unknown")) {
        Path target = directory.resolve(endpoint);
        transport.download(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + endpoint),
            target,
            progress);
        assertArrayEquals(bytes, Files.readAllBytes(target));
      }
      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  transport.download(
                      URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/missing"),
                      directory.resolve("failed"),
                      progress));
      assertTrue(failure.getMessage().contains("HTTP 404"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void timesOutWhenABodyStopsArriving() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var release = new CountDownLatch(1);
    server.createContext(
        "/stalled",
        exchange -> {
          exchange.sendResponseHeaders(200, 100);
          try (var body = exchange.getResponseBody()) {
            body.write(1);
            body.flush();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });
    server.start();
    try (var transport = new UpdateTransport(Duration.ofMillis(200));
        var progress = new UpdateProgress(new PrintWriter(new StringWriter()), false)) {
      assertTimeoutPreemptively(
          Duration.ofSeconds(3),
          () ->
              assertThrows(
                  IOException.class,
                  () ->
                      transport.download(
                          URI.create(
                              "http://127.0.0.1:" + server.getAddress().getPort() + "/stalled"),
                          directory.resolve("stalled"),
                          progress)));
    } finally {
      release.countDown();
      server.stop(0);
    }
  }
}
