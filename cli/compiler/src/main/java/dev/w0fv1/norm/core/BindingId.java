package dev.w0fv1.norm.core;

import java.util.Objects;

public record BindingId(ContentHash hash) {
  public BindingId {
    Objects.requireNonNull(hash, "hash");
  }

  public static BindingId forArtifact(CoreArtifact artifact) {
    return new BindingId(
        ContentHasher.hash(
            "norm:execution-binding:v1\0",
            CoreIdentityVersion.CURRENT,
            CoreArtifactIdentity.binding(artifact)));
  }
}
