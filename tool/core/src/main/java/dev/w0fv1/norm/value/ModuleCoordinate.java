package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleCoordinate(String name, int version) implements Comparable<ModuleCoordinate> {
  public ModuleCoordinate {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("module name must not be blank");
    if (version < 1 && !(name.equals("anonymous") && version == 0)) {
      throw new IllegalArgumentException("named module version must be positive");
    }
  }

  @Override
  public int compareTo(ModuleCoordinate other) {
    int nameOrder = name.compareTo(Objects.requireNonNull(other, "other").name);
    return nameOrder != 0 ? nameOrder : Integer.compare(version, other.version);
  }
}
