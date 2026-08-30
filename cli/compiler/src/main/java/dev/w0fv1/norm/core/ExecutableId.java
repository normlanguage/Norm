package dev.w0fv1.norm.core;

import java.util.Objects;

public record ExecutableId(ContentHash hash) implements Comparable<ExecutableId> {
  private static final String DOMAIN = "norm:executable:v1\0";

  public ExecutableId {
    Objects.requireNonNull(hash, "hash");
  }

  public static ExecutableId forArtifact(CoreArtifact artifact, String backendAbi) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(backendAbi, "backendAbi");
    byte[] canonical =
        new CanonicalWriter()
            .writeTag("executable")
            .writeString(backendAbi)
            .writeBytes(CoreCodeId.forArtifact(artifact).hash().bytes())
            .writeBytes(MetadataId.forArtifact(artifact).hash().bytes())
            .toByteArray();
    return new ExecutableId(ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, canonical));
  }

  @Override
  public int compareTo(ExecutableId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
