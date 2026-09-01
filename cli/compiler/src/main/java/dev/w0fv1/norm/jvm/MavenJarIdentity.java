package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import java.util.Objects;

public record MavenJarIdentity(MavenArtifactCoordinate coordinate, String classifier)
    implements JarArtifactIdentity {
  public MavenJarIdentity {
    Objects.requireNonNull(coordinate, "coordinate");
    Objects.requireNonNull(classifier, "classifier");
  }

  public MavenJarIdentity(MavenArtifactCoordinate coordinate) {
    this(coordinate, "");
  }

  @Override
  public String canonical() {
    return "maven:" + coordinate.notation() + (classifier.isEmpty() ? "" : ":jar:" + classifier);
  }
}
