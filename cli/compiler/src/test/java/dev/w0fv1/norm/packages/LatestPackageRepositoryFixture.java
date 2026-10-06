package dev.w0fv1.norm.packages;

import com.sun.net.httpserver.HttpServer;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class LatestPackageRepositoryFixture implements AutoCloseable {
  private final HttpServer server;
  private final NormPackageResolver resolver;
  private final AtomicInteger version = new AtomicInteger(1);
  private final AtomicInteger requests = new AtomicInteger();

  public LatestPackageRepositoryFixture(Path directory) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/registry.json",
        exchange -> {
          byte[] body =
              """
          {"formatVersion":1,"packages":[{"name":"sample.library","owner":"normlanguage","repository":"sample-library"}]}
          """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
          exchange.close();
        });
    server.createContext(
        "/normlanguage/sample-library/releases/latest",
        exchange -> {
          requests.incrementAndGet();
          exchange
              .getResponseHeaders()
              .set("Location", "/normlanguage/sample-library/releases/tag/v" + version.get());
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.createContext(
        "/normlanguage/sample-library/releases/tag/",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    resolver =
        new NormPackageResolver(
            directory.resolve("local"),
            directory.resolve("cache"),
            Map.of(
                ModuleRepositoryId.GITHUB,
                new GitHubPackageRepository(root.resolve("registry.json"), root)));
  }

  public NormPackageResolver resolver() {
    return resolver;
  }

  public void version(int next) {
    version.set(next);
  }

  public int requests() {
    return requests.get();
  }

  @Override
  public void close() {
    resolver.close();
    server.stop(0);
  }
}
