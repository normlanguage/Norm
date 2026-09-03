package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleRepositoryId(String value) implements Comparable<ModuleRepositoryId> {
  public static final ModuleRepositoryId GITHUB = new ModuleRepositoryId("github");

  public ModuleRepositoryId {
    Objects.requireNonNull(value, "value");
    if (!value.matches("[a-z][a-z0-9-]*")) {
      throw new IllegalArgumentException("invalid module repository '" + value + "'");
    }
  }

  @Override
  public int compareTo(ModuleRepositoryId other) {
    return value.compareTo(Objects.requireNonNull(other, "other").value);
  }
}
