package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleRequirement(ModuleCoordinate coordinate, boolean exported) {
  public ModuleRequirement {
    Objects.requireNonNull(coordinate, "coordinate");
  }

  public ModuleRequirement(String name, int version) {
    this(new ModuleCoordinate(name, version), false);
  }

  public ModuleRequirement(String name, int version, boolean exported) {
    this(new ModuleCoordinate(name, version), exported);
  }

  public String name() {
    return coordinate.name();
  }

  public int version() {
    return coordinate.version();
  }
}
