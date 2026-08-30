package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.Objects;
import java.util.Optional;

public record CoreNominalTypeKey(
    ModuleCoordinate module,
    String packageName,
    String name,
    CoreVisibility visibility,
    Optional<String> privateSourcePath) {
  public CoreNominalTypeKey {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(packageName, "packageName");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("nominal type name must not be blank");
    Objects.requireNonNull(visibility, "visibility");
    privateSourcePath = Objects.requireNonNull(privateSourcePath, "privateSourcePath");
    if ((visibility == CoreVisibility.PRIVATE) != privateSourcePath.isPresent()) {
      throw new IllegalArgumentException("private nominal types require a source path");
    }
    privateSourcePath =
        privateSourcePath.map(path -> new ModuleSourceCoordinate(module, path).relativePath());
  }
}
