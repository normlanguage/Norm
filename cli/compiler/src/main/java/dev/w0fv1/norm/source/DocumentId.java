package dev.w0fv1.norm.source;

import java.net.URI;
import java.util.Objects;

public record DocumentId(URI uri) {
  public DocumentId {
    Objects.requireNonNull(uri, "uri");
  }

  public static DocumentId of(String uri) {
    return new DocumentId(URI.create(uri));
  }
}
