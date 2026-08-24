package dev.w0fv1.norm.core;

import java.util.Objects;

public record InterfaceId(ContentHash hash) implements Comparable<InterfaceId> {
  public InterfaceId {
    Objects.requireNonNull(hash, "hash");
  }

  public static InterfaceId parse(String text) {
    return new InterfaceId(ContentHash.parse(text));
  }

  @Override
  public int compareTo(InterfaceId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
