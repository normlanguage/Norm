package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public final class ContentHash implements Comparable<ContentHash> {
  public static final int BYTE_LENGTH = 32;

  private static final int HEX_LENGTH = BYTE_LENGTH * 2;
  private static final HexFormat HEX = HexFormat.of();

  private final byte[] bytes;

  private ContentHash(byte[] bytes) {
    this.bytes = bytes;
  }

  public static ContentHash of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length != BYTE_LENGTH) {
      throw new IllegalArgumentException("content hash must contain exactly 32 bytes");
    }
    return new ContentHash(bytes.clone());
  }

  public static ContentHash parse(String hex) {
    Objects.requireNonNull(hex, "hex");
    if (hex.length() != HEX_LENGTH) {
      throw new IllegalArgumentException(
          "content hash must contain exactly 64 hexadecimal characters");
    }
    try {
      return of(HEX.parseHex(hex));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "content hash contains a non-hexadecimal character", exception);
    }
  }

  public byte[] bytes() {
    return bytes.clone();
  }

  public String hex() {
    return HEX.formatHex(bytes);
  }

  @Override
  public int compareTo(ContentHash other) {
    Objects.requireNonNull(other, "other");
    return Arrays.compareUnsigned(bytes, other.bytes);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof ContentHash contentHash && Arrays.equals(bytes, contentHash.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return hex();
  }
}
