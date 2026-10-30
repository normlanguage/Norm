package dev.w0fv1.norm.cli.update;

import dev.w0fv1.norm.platform.jdk.EnvironmentProxySelector;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.util.EntityUtils;

final class UpdateTransport implements AutoCloseable {
  private final CloseableHttpClient client;
  private final Duration timeout;

  UpdateTransport(Duration timeout) {
    this.timeout = timeout;
    int milliseconds = Math.toIntExact(timeout.toMillis());
    client =
        HttpClients.custom()
            .setRoutePlanner(new SystemDefaultRoutePlanner(EnvironmentProxySelector.system()))
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(Math.min(10000, milliseconds))
                    .setConnectionRequestTimeout(milliseconds)
                    .setSocketTimeout(milliseconds)
                    .build())
            .disableAutomaticRetries()
            .build();
  }

  String read(URI uri) throws IOException {
    var request = new HttpGet(uri);
    request.setHeader("Accept", "application/vnd.github+json");
    request.setHeader("User-Agent", "Norm-Updater");
    try (var response = client.execute(request)) {
      if (response.getStatusLine().getStatusCode() != 200)
        throw new IOException(
            "cannot query latest Norm release: HTTP " + response.getStatusLine().getStatusCode());
      return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    } catch (SocketTimeoutException exception) {
      throw new IOException(
          "timed out checking Norm releases; no response data for " + timeout.toSeconds() + "s",
          exception);
    }
  }

  void download(URI uri, Path target, UpdateProgress progress) throws IOException {
    try (var response = client.execute(new HttpGet(uri))) {
      if (response.getStatusLine().getStatusCode() != 200)
        throw new IOException(
            "cannot download Norm CLI: HTTP " + response.getStatusLine().getStatusCode());
      long total = response.getEntity().getContentLength();
      long transferred = 0;
      progress.download(0, total);
      try (var source = response.getEntity().getContent();
          var output = Files.newOutputStream(target)) {
        byte[] buffer = new byte[64 * 1024];
        for (int read; (read = source.read(buffer)) != -1; ) {
          output.write(buffer, 0, read);
          transferred += read;
          progress.download(transferred, total);
        }
      }
      progress.refresh();
    } catch (SocketTimeoutException exception) {
      throw new IOException(
          "Norm download stalled; no response data for "
              + timeout.toSeconds()
              + "s; run 'norm update --cli' to retry",
          exception);
    }
  }

  @Override
  public void close() throws IOException {
    client.close();
  }
}
