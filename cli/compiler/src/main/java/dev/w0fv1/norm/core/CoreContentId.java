package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreContentId(ContentHash hash) {
  public CoreContentId {
    Objects.requireNonNull(hash, "hash");
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
