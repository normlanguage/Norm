package dev.w0fv1.norm.jvm;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class RepositoryHttp {
  private RepositoryHttp() {}

  static <T> HttpResponse<T> send(
      HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    try {
      return client.send(request, handler);
    } catch (IOException exception) {
      throw new IOException(
          "request failed: " + request.method() + " " + request.uri() + " (" + exception + ")",
          exception);
    }
  }
}
