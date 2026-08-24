package dev.w0fv1.norm.core;

import java.util.Objects;

public record DefinitionGroupId(ContentHash hash) implements Comparable<DefinitionGroupId> {
  public DefinitionGroupId {
    Objects.requireNonNull(hash, "hash");
  }

  public static DefinitionGroupId parse(String text) {
    return new DefinitionGroupId(ContentHash.parse(text));
  }

  @Override
  public int compareTo(DefinitionGroupId other) {
    Objects.requireNonNull(other, "other");
    return hash.compareTo(other.hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
