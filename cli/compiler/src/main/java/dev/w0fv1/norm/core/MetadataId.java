package dev.w0fv1.norm.core;

import java.util.Objects;

public record MetadataId(ContentHash hash) implements Comparable<MetadataId> {
  private static final String DOMAIN = "norm:metadata:v1\0";

  public MetadataId {
    Objects.requireNonNull(hash, "hash");
  }

  public static MetadataId forArtifact(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    return new MetadataId(
        ContentHasher.hash(
            DOMAIN, CoreIdentityVersion.CURRENT, CoreArtifactIdentity.metadata(artifact)));
  }

  @Override
  public int compareTo(MetadataId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
