package dev.w0fv1.norm.value;

import dev.w0fv1.norm.syntax.LanguageSyntax;
import java.util.Objects;

public record ModuleCoordinate(String name, int version) implements Comparable<ModuleCoordinate> {
  public ModuleCoordinate {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("module name must not be blank");
    for (String segment : name.split("\\.", -1)) {
      if (!LanguageSyntax.isIdentifier(segment)) {
        throw new IllegalArgumentException("invalid module name '" + name + "'");
      }
    }
    if (version < 0) throw new IllegalArgumentException("module version must not be negative");
  }

  @Override
  public int compareTo(ModuleCoordinate other) {
    int nameOrder = name.compareTo(Objects.requireNonNull(other, "other").name);
    return nameOrder != 0 ? nameOrder : Integer.compare(version, other.version);
  }
}
