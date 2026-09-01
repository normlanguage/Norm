package dev.w0fv1.norm.value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

public record Sha256Digest(String value) {
  public Sha256Digest {
    Objects.requireNonNull(value, "value");
    value = value.toLowerCase(Locale.ROOT);
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("SHA-256 digest must contain 64 hexadecimal characters");
    }
  }

  public static Sha256Digest parse(String value) {
    return new Sha256Digest(value);
  }

  public static Sha256Digest compute(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    MessageDigest digest = algorithm();
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) digest.update(buffer, 0, read);
      }
    }
    return fromBytes(digest.digest());
  }

  public static Sha256Digest compute(byte[] content) {
    return fromBytes(algorithm().digest(Objects.requireNonNull(content, "content")));
  }

  private static MessageDigest algorithm() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static Sha256Digest fromBytes(byte[] bytes) {
    return new Sha256Digest(java.util.HexFormat.of().formatHex(bytes));
  }

  @Override
  public String toString() {
    return "sha256:" + value;
  }
}
