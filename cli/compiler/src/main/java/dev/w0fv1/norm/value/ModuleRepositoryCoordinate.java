package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleRepositoryCoordinate(String group, String artifact, String version) {
  public ModuleRepositoryCoordinate {
    new MavenArtifactCoordinate(group, artifact, version);
  }

  public static ModuleRepositoryCoordinate from(ModuleCoordinate module) {
    Objects.requireNonNull(module, "module");
    int separator = module.name().lastIndexOf('.');
    if (separator < 1 || separator == module.name().length() - 1) {
      throw new IllegalArgumentException(
          "published module name must contain a namespace and artifact: " + module.name());
    }
    return new ModuleRepositoryCoordinate(
        module.name().substring(0, separator),
        module.name().substring(separator + 1),
        Integer.toString(module.version()));
  }

  public String notation() {
    return group + ":" + artifact + ":" + version;
  }
}
