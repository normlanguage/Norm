package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleRequirement(
    ModuleRepositoryId repository, ModuleCoordinate coordinate, boolean exported) {
  public ModuleRequirement {
    Objects.requireNonNull(repository, "repository");
    Objects.requireNonNull(coordinate, "coordinate");
    if (coordinate.version() < 1) {
      throw new IllegalArgumentException("module dependency version must be positive");
    }
  }

  public ModuleRequirement(String repository, String name, int version, boolean exported) {
    this(new ModuleRepositoryId(repository), new ModuleCoordinate(name, version), exported);
  }

  public String name() {
    return coordinate.name();
  }

  public int version() {
    return coordinate.version();
  }
}
