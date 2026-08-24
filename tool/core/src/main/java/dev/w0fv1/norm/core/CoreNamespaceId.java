package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreNamespaceId(ContentHash hash) implements Comparable<CoreNamespaceId> {
  public CoreNamespaceId {
    Objects.requireNonNull(hash, "hash");
  }

  public static CoreNamespaceId parse(String text) {
    return new CoreNamespaceId(ContentHash.parse(text));
  }

  @Override
  public int compareTo(CoreNamespaceId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
