package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreCodeId(ContentHash hash) implements Comparable<CoreCodeId> {
  private static final String DOMAIN = "norm:core-code:v1\0";

  public CoreCodeId {
    Objects.requireNonNull(hash, "hash");
  }

  public static CoreCodeId forArtifact(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    return new CoreCodeId(
        ContentHasher.hash(
            DOMAIN, CoreIdentityVersion.CURRENT, CoreArtifactIdentity.code(artifact)));
  }

  @Override
  public int compareTo(CoreCodeId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
