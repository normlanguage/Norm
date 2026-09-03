package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.value.DocumentId;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

final class VirtualDocumentUri {
  static final String SCHEME = "norm-source";

  private VirtualDocumentUri() {}

  static String encode(DocumentId document) {
    String identity =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(document.uri().toString().getBytes(StandardCharsets.UTF_8));
    String name = sourceName(document.uri());
    try {
      return new URI(SCHEME, null, "/" + identity + "/" + name, null).toString();
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("source document URI cannot be represented", exception);
    }
  }

  static Optional<DocumentId> decode(String value) {
    try {
      URI uri = URI.create(value);
      if (!SCHEME.equals(uri.getScheme())) return Optional.empty();
      String path = uri.getPath();
      int separator = path.indexOf('/', 1);
      if (separator < 2) return Optional.empty();
      String encoded = path.substring(1, separator);
      String document = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      return Optional.of(DocumentId.of(document));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static String sourceName(URI uri) {
    String path = uri.getPath();
    if (path == null || path.isBlank()) return "source.norm";
    int separator = path.lastIndexOf('/');
    String name = path.substring(separator + 1);
    return name.isBlank() ? "source.norm" : name;
  }
}
