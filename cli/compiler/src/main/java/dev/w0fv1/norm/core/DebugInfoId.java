package dev.w0fv1.norm.core;

import java.util.Objects;

public record DebugInfoId(ContentHash hash) implements Comparable<DebugInfoId> {
  private static final String DOMAIN = "norm:debug-info:v1\0";

  public DebugInfoId {
    Objects.requireNonNull(hash, "hash");
  }

  public static DebugInfoId forArtifact(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    return new DebugInfoId(
        ContentHasher.hash(
            DOMAIN, CoreIdentityVersion.CURRENT, CoreArtifactIdentity.debug(artifact)));
  }

  @Override
  public int compareTo(DebugInfoId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
