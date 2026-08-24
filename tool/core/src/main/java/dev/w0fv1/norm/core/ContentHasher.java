package dev.w0fv1.norm.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

final class ContentHasher {
  private ContentHasher() {}

  static ContentHash hash(String domain, CoreIdentityVersion version, byte[] canonical) {
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(canonical, "canonical");
    byte[] separator = domain.getBytes(StandardCharsets.US_ASCII);
    if (!domain.equals(new String(separator, StandardCharsets.US_ASCII))) {
      throw new IllegalArgumentException("content hash domain must be ASCII");
    }
    MessageDigest digest = sha256();
    digest.update(separator);
    digest.update(
        ByteBuffer.allocate(Integer.BYTES * 3)
            .putInt(version.schema().code())
            .putInt(version.semantics().code())
            .putInt(canonical.length)
            .array());
    digest.update(canonical);
    return ContentHash.of(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
