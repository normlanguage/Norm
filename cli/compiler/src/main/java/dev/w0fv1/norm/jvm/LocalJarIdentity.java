package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.Sha256Digest;
import java.util.Objects;

public record LocalJarIdentity(Sha256Digest content) implements JarArtifactIdentity {
  public LocalJarIdentity {
    Objects.requireNonNull(content, "content");
  }

  @Override
  public String canonical() {
    return "local:" + content.value();
  }
}
