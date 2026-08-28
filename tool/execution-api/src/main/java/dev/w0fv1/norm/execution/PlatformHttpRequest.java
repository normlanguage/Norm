package dev.w0fv1.norm.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlatformHttpRequest {
  private final HttpMethod method;
  private final String uri;
  private final List<PlatformHttpHeader> headers;
  private final Optional<byte[]> body;

  public PlatformHttpRequest(
      HttpMethod method, String uri, List<PlatformHttpHeader> headers, Optional<byte[]> body) {
    this.method = Objects.requireNonNull(method, "method");
    this.uri = Objects.requireNonNull(uri, "uri");
    this.headers = List.copyOf(headers);
    this.body = Objects.requireNonNull(body, "body").map(byte[]::clone);
  }

  public HttpMethod method() {
    return method;
  }

  public String uri() {
    return uri;
  }

  public List<PlatformHttpHeader> headers() {
    return headers;
  }

  public Optional<byte[]> body() {
    return body.map(byte[]::clone);
  }
}
