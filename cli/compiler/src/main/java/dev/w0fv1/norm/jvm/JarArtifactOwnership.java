package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record JarArtifactOwnership(
    ResolvedJarArtifact owner, Set<MavenArtifactCoordinate> components) {
  public JarArtifactOwnership {
    Objects.requireNonNull(owner, "owner");
    components = Set.copyOf(components);
  }

  public void validate(List<ResolvedJarArtifact> classpath) {
    if (classpath.stream().noneMatch(artifact -> artifact.file().equals(owner.file()))) return;
    for (var artifact : classpath) {
      if (artifact.file().equals(owner.file())
          || !(artifact.identity() instanceof MavenJarIdentity maven)
          || !maven.classifier().isEmpty()) continue;
      for (var component : components) {
        var coordinate = maven.coordinate();
        if (component.group().equals(coordinate.group())
            && component.artifact().equals(coordinate.artifact())) {
          throw new IllegalArgumentException(
              "Java component "
                  + component.notation()
                  + " is contained in "
                  + owner.file()
                  + " and cannot be independently replaced by "
                  + coordinate.notation()
                  + " in "
                  + artifact.file());
        }
      }
    }
  }
}
