package dev.w0fv1.norm.core;

import java.util.Objects;

public record ArtifactId(ContentHash hash) implements Comparable<ArtifactId> {
  private static final String DOMAIN = "norm:artifact:v2\0";

  public ArtifactId {
    Objects.requireNonNull(hash, "hash");
  }

  public static ArtifactId parse(String text) {
    return new ArtifactId(ContentHash.parse(text));
  }

  public static ArtifactId forArtifact(CoreArtifact artifact, String backendAbi) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(backendAbi, "backendAbi");
    byte[] canonical =
        new CanonicalWriter()
            .writeTag("artifact")
            .writeString(backendAbi)
            .writeBytes(CoreCodeId.forArtifact(artifact).hash().bytes())
            .writeBytes(PublicAbiId.forArtifact(artifact).hash().bytes())
            .writeBytes(CoreArtifactIdentity.linkage(artifact))
            .writeBytes(DebugInfoId.forArtifact(artifact).hash().bytes())
            .writeBytes(MetadataId.forArtifact(artifact).hash().bytes())
            .toByteArray();
    return new ArtifactId(ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, canonical));
  }

  @Override
  public int compareTo(ArtifactId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
