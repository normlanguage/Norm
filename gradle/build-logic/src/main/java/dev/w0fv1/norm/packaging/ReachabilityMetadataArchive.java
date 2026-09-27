package dev.w0fv1.norm.packaging;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ReachabilityMetadataArchive {
  private static final String ARCHIVE_VERSION = "1.0.13";
  public static final ReachabilityMetadataArchive OFFICIAL =
      new ReachabilityMetadataArchive(
          URI.create(
              "https://github.com/oracle/graalvm-reachability-metadata/releases/download/"
                  + ARCHIVE_VERSION
                  + "/graalvm-reachability-metadata-"
                  + ARCHIVE_VERSION
                  + ".zip"),
          "b94893e10448a37a604d24758418dc006223a64827146f0886af172bbbdd818a");

  private final URI source;
  private final String expectedSha256;

  ReachabilityMetadataArchive(URI source, String expectedSha256) {
    this.source = source;
    this.expectedSha256 = expectedSha256;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Usage: ReachabilityMetadataArchive <output> <local archive or empty> <offline>");
    }
    OFFICIAL.prepare(
        args[1].isEmpty() ? null : Path.of(args[1]),
        Boolean.parseBoolean(args[2]),
        Path.of(args[0]));
  }

  public void prepare(Path localArchive, boolean offline, Path output) throws IOException {
    if (offline && localArchive == null) {
      throw new IOException("offline build requires a local GraalVM reachability metadata archive");
    }
    Path destination = output.toAbsolutePath();
    Files.createDirectories(destination.getParent());
    Path temporary =
        Files.createTempFile(destination.getParent(), "reachability-metadata-", ".zip");
    try {
      if (localArchive != null) {
        Files.copy(localArchive, temporary, StandardCopyOption.REPLACE_EXISTING);
      } else {
        download(temporary);
      }
      String actual = sha256(temporary);
      if (!actual.equalsIgnoreCase(expectedSha256)) {
        throw new IOException(
            "GraalVM reachability metadata checksum mismatch: expected "
                + expectedSha256
                + ", got "
                + actual);
      }
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void download(Path destination) throws IOException {
    var builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS);
    for (String name : new String[] {"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
      String proxy = System.getenv(name);
      if (proxy == null || proxy.isBlank()) continue;
      URI uri = URI.create(proxy);
      builder.proxy(
          ProxySelector.of(
              InetSocketAddress.createUnresolved(
                  uri.getHost(), uri.getPort() >= 0 ? uri.getPort() : 80)));
      break;
    }
    try {
      var client = builder.build();
      var request = HttpRequest.newBuilder(source).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException(
            "Cannot download GraalVM reachability metadata: HTTP " + response.statusCode());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("GraalVM reachability metadata download interrupted", exception);
    }
  }

  private static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
        input.transferTo(java.io.OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
