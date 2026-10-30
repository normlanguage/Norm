package dev.w0fv1.norm.cli.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliUpdateManagerTest {
  @TempDir Path directory;

  @Test
  void fallsBackToOfficialDownloadAndReportsDeferredInstallationTruthfully() throws Exception {
    var server = server(200);
    var host = new Host(directory);
    host.manager = Optional.of(UpdateHost.PackageManager.SCOOP);
    var output = new StringWriter();
    server.start();
    try {
      var result =
          new CliUpdateManager(uri(server), host, "0.0.0-dev").update(new PrintWriter(output));
      assertTrue(result.deferred());
      assertArrayEquals(
          "payload".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(host.staged));
      assertEquals("old", Files.readString(host.target));
      String text = output.toString();
      assertTrue(text.indexOf("Checking Norm updates") < text.indexOf("Updating Norm via Scoop"));
      assertTrue(text.contains("Scoop update failed (exit 1)"));
      assertTrue(text.contains("Latest version: v1.2.3"));
      assertTrue(text.contains("development build"));
      assertTrue(text.contains("Installation will continue after this command exits"));
      assertTrue(text.contains("norm --version"));
      assertFalse(text.contains("Updated Norm"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void skipsInstallationWhenAlreadyCurrent() throws Exception {
    var server = server(200);
    var host = new Host(directory);
    var output = new StringWriter();
    server.start();
    try {
      var result = new CliUpdateManager(uri(server), host, "1.2.3").update(new PrintWriter(output));
      assertFalse(result.deferred());
      assertNull(host.staged);
      assertTrue(output.toString().contains("Norm is up to date (v1.2.3)"));
      assertFalse(output.toString().contains("Downloading Norm"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failedDownloadsLeaveTheInstalledLauncherAndNoStagedFile() throws Exception {
    var server = server(503);
    var host = new Host(directory);
    var output = new StringWriter();
    server.start();
    try {
      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  new CliUpdateManager(uri(server), host, "1.0.0").update(new PrintWriter(output)));
      assertTrue(failure.getMessage().contains("HTTP 503"));
      assertNull(host.staged);
      assertEquals("old", Files.readString(host.target));
      try (var files = Files.list(directory)) {
        assertEquals(1, files.count());
      }
      assertFalse(output.toString().contains("Downloaded Norm"));
      assertFalse(output.toString().contains("Installation will continue"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void successfulPackageManagerDoesNotNeedTheReleaseService() throws Exception {
    var host = new Host(directory);
    host.manager = Optional.of(UpdateHost.PackageManager.BREW);
    host.exit = 0;
    var output = new StringWriter();
    var result =
        new CliUpdateManager(URI.create("http://127.0.0.1:1/latest"), host, "1.0.0")
            .update(new PrintWriter(output));
    assertFalse(result.deferred());
    assertNull(host.staged);
    assertTrue(output.toString().contains("Homebrew update completed"));
    assertTrue(output.toString().contains("package output"));
  }

  private HttpServer server(int assetStatus) throws IOException {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/latest",
        exchange -> {
          String body =
              "{\"tag_name\":\"v1.2.3\",\"assets\":[{\"name\":\"norm.exe\",\"browser_download_url\":\"http://127.0.0.1:"
                  + server.getAddress().getPort()
                  + "/asset\"}]}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
          }
        });
    server.createContext(
        "/asset",
        exchange -> {
          byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(assetStatus, bytes.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
          }
        });
    return server;
  }

  private URI uri(HttpServer server) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
  }

  private static final class Host implements UpdateHost {
    final Path target;
    Path staged;
    int exit = 1;
    Optional<PackageManager> manager = Optional.empty();

    Host(Path directory) throws IOException {
      target = Files.writeString(directory.resolve("norm.exe"), "old");
    }

    public Optional<PackageManager> packageManager() {
      return manager;
    }

    public int updatePackage(PackageManager manager, Consumer<String> output) {
      output.accept("package output");
      return exit;
    }

    public Path launcherPath() {
      return target;
    }

    public void install(Path staged, Path target) {
      this.staged = staged;
    }
  }
}
