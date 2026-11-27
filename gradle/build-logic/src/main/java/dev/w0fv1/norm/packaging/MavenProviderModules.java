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
          new Component("org.apache.maven.v3.resolver.provider", "maven-resolver-provider"),
          new Component("org.apache.maven.v3.model.builder", "maven-model-builder"),
          new Component("org.apache.maven.v3.model", "maven-model"),
          new Component("org.apache.maven.v3.repository.metadata", "maven-repository-metadata"),
          new Component("org.apache.maven.v3.artifact", "maven-artifact"),
          new Component("org.apache.maven.v3.builder", "maven-builder-support"));

  private MavenProviderModules() {}
}
