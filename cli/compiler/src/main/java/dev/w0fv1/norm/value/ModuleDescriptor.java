package dev.w0fv1.norm.value;

import dev.w0fv1.norm.syntax.LanguageSyntax;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModuleDescriptor(
    ModuleCoordinate coordinate,
    List<String> exports,
    List<ModuleRequirement> dependencies,
    Optional<JarBinding> binding) {
  public ModuleDescriptor {
    Objects.requireNonNull(coordinate, "coordinate");
    exports = List.copyOf(exports);
    dependencies = List.copyOf(dependencies);
    Objects.requireNonNull(binding, "binding");
    HashSet<String> unique = new HashSet<>();
    for (String exportedName : exports) {
      validateQualifiedName(exportedName, "export");
      if (!unique.add(exportedName)) {
        throw new IllegalArgumentException("duplicate exported source '" + exportedName + "'");
      }
    }
    if (binding.isPresent()) {
      JarBinding value = binding.orElseThrow();
      if (!value.api().isEmpty() && exports.size() != value.api().size()) {
        throw new IllegalArgumentException(
            "module exports must match the declared JAR binding API type count");
      }
    }
    HashSet<ModuleCoordinate> uniqueDependencies = new HashSet<>();
    for (ModuleRequirement dependency : dependencies) {
      Objects.requireNonNull(dependency, "dependency");
      if (!uniqueDependencies.add(dependency.coordinate())) {
        throw new IllegalArgumentException(
            "duplicate module dependency '" + dependency.name() + "@" + dependency.version() + "'");
      }
    }
  }

  public ModuleDescriptor(String name, int version, List<String> exports) {
    this(new ModuleCoordinate(name, version), exports, List.of(), Optional.empty());
  }

  public ModuleDescriptor(
      String name, int version, List<String> exports, List<ModuleRequirement> dependencies) {
    this(new ModuleCoordinate(name, version), exports, dependencies, Optional.empty());
  }

  public ModuleDescriptor(
      ModuleCoordinate coordinate, List<String> exports, List<ModuleRequirement> dependencies) {
    this(coordinate, exports, dependencies, Optional.empty());
  }

  public String name() {
    return coordinate.name();
  }

  public int version() {
    return coordinate.version();
  }

  public String sourcePath(String exportedName) {
    return (name() + "." + exportedName).replace('.', '/') + ".norm";
  }

  public ModuleDescriptor withExports(List<String> values) {
    return new ModuleDescriptor(coordinate, values, dependencies, binding);
  }

  private static void validateQualifiedName(String value, String role) {
    Objects.requireNonNull(value, role);
    if (value.isBlank()) throw new IllegalArgumentException(role + " name must not be blank");
    for (String segment : value.split("\\.", -1)) {
      if (!LanguageSyntax.isIdentifier(segment)) {
        throw new IllegalArgumentException("invalid " + role + " name '" + value + "'");
      }
    }
  }
}
