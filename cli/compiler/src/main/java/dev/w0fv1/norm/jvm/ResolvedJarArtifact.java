package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.Objects;

public record ResolvedJarArtifact(JarArtifactIdentity identity, Path file, Sha256Digest content) {
  public ResolvedJarArtifact {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(content, "content");
    file = file.toAbsolutePath().normalize();
  }
}
