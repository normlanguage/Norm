package dev.w0fv1.norm.core;

import java.util.Objects;

public record PublicAbiId(ContentHash hash) implements Comparable<PublicAbiId> {
  private static final String DOMAIN = "norm:public-abi:v1\0";

  public PublicAbiId {
    Objects.requireNonNull(hash, "hash");
  }

  public static PublicAbiId forArtifact(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    var bindings =
        artifact.namespace().bindings().stream()
            .filter(CoreBinding::exported)
            .map(CoreNamespace::canonicalBinding)
            .sorted(java.util.Arrays::compareUnsigned)
            .toList();
    CanonicalWriter writer = new CanonicalWriter().writeTag("public-abi").writeInt(bindings.size());
    bindings.forEach(writer::writeBytes);
    return new PublicAbiId(
        ContentHasher.hash(DOMAIN, CoreIdentityVersion.CURRENT, writer.toByteArray()));
  }

  @Override
  public int compareTo(PublicAbiId other) {
    return hash.compareTo(Objects.requireNonNull(other, "other").hash);
  }

  @Override
  public String toString() {
    return hash.toString();
  }
}
