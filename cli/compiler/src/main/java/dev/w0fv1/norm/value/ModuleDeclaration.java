package dev.w0fv1.norm.value;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ModuleDeclaration(
    Optional<String> name,
    OptionalInt version,
    List<String> exports,
    List<ModuleDependency> dependencies,
    Optional<JarBinding> binding) {
  public ModuleDeclaration {
    name = Objects.requireNonNull(name, "name");
    version = Objects.requireNonNull(version, "version");
    exports = List.copyOf(exports);
    dependencies = List.copyOf(dependencies);
    binding = Objects.requireNonNull(binding, "binding");
    name.ifPresent(value -> new ModuleCoordinate(value, 1));
    if (version.isPresent() && version.getAsInt() < 1) {
      throw new IllegalArgumentException("module version must be positive");
    }
  }

  public ModuleDeclaration(
      String name,
      Integer version,
      List<String> exports,
      List<ModuleDependency> dependencies,
      Optional<JarBinding> binding) {
    this(
        Optional.ofNullable(name).filter(value -> !value.isBlank()),
        version == null ? OptionalInt.empty() : OptionalInt.of(version),
        exports,
        dependencies,
        binding);
  }
}
