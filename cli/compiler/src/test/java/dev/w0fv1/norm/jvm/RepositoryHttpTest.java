package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class RepositoryHttpTest {
  @Test
  void includesTheRequestedResourceAndPreservesTransportFailure() throws Exception {
    try (var server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        var client = HttpClient.newBuilder().proxy(java.net.ProxySelector.of(null)).build()) {
      URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/registry.json");
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(200)).GET().build();
      IOException failure =
          assertThrows(
              IOException.class,
              () -> RepositoryHttp.send(client, request, HttpResponse.BodyHandlers.discarding()));
      assertTrue(failure.getMessage().contains(uri.toString()));
      assertInstanceOf(HttpTimeoutException.class, failure.getCause());
    }
  }
}
