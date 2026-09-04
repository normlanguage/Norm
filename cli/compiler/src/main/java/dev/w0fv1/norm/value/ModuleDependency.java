package dev.w0fv1.norm.value;

import java.util.Objects;
import java.util.OptionalInt;

public record ModuleDependency(
    ModuleRepositoryId repository, String name, OptionalInt version, boolean exported) {
  public ModuleDependency {
    Objects.requireNonNull(repository, "repository");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(version, "version");
    new ModuleCoordinate(name, 1);
    if (version.isPresent() && version.getAsInt() < 1) {
      throw new IllegalArgumentException("module dependency version must be positive");
    }
  }

  public ModuleDependency(String repository, String name, Integer version, boolean exported) {
    this(
        new ModuleRepositoryId(repository),
        name,
        version == null ? OptionalInt.empty() : OptionalInt.of(version),
        exported);
  }

  public ModuleRequirement resolved(int resolvedVersion) {
    return new ModuleRequirement(repository, new ModuleCoordinate(name, resolvedVersion), exported);
  }
}
