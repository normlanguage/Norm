package dev.w0fv1.norm.value;

import java.util.Objects;
import java.util.Optional;

public record MavenJarTarget(MavenArtifactCoordinate coordinate, Optional<Sha256Digest> resolution)
    implements JarTarget {
  public MavenJarTarget {
    Objects.requireNonNull(coordinate, "coordinate");
    Objects.requireNonNull(resolution, "resolution");
  }
}
