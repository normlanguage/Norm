package dev.w0fv1.norm.packaging;

import java.util.List;

public final class MavenProviderModules {
  public record Component(String moduleName, String artifact) {
    public String groupArtifact() {
      return "org.apache.maven:" + artifact;
    }
  }

  public static final List<Component> COMPONENTS =
      List.of(
          new Component("maven.resolver.provider", "maven-resolver-provider"),
          new Component("maven.model.builder", "maven-model-builder"),
          new Component("maven.model", "maven-model"),
          new Component("maven.repository.metadata", "maven-repository-metadata"),
          new Component("maven.artifact", "maven-artifact"),
          new Component("maven.builder.support", "maven-builder-support"));

  private MavenProviderModules() {}
}
