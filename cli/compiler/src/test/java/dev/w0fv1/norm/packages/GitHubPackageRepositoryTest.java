package dev.w0fv1.norm.packages;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitHubPackageRepositoryTest {
  @TempDir Path directory;

  @Test
  void selectsHighestStableIntegerAcrossPagesInsteadOfLatestLabel() throws Exception {
    var registry =
        Files.writeString(
            directory.resolve("registry.json"),
            """
        {"formatVersion":1,"packages":[{"name":"agent","owner":"normlanguage","repository":"agent"}]}
        """);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    server.createContext(
        "/repos/normlanguage/agent/releases",
        exchange -> {
          calls.incrementAndGet();
          String body =
              exchange.getRequestURI().getQuery().contains("page=1&")
                  ? "["
                      + String.join(
                          ",",
                          java.util.Collections.nCopies(
                              100, "{\"tag_name\":\"v1\",\"draft\":false,\"prerelease\":false}"))
                      + "]"
                  : """
          [{"tag_name":"v3","draft":false,"prerelease":false},
           {"tag_name":"v2","draft":false,"prerelease":false},
           {"tag_name":"v10","draft":false,"prerelease":true},
           {"tag_name":"v11","draft":true,"prerelease":false},
           {"tag_name":"v12-beta","draft":false,"prerelease":false}]
          """;
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    try (var client = HttpClient.newHttpClient()) {
      var root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
      var repository = new GitHubPackageRepository(registry.toUri(), root, root.resolve("repos/"));
      assertEquals(3, repository.latestVersion("agent", client));
      assertEquals(2, calls.get());
    } finally {
      server.stop(0);
    }
  }
}
