package dev.w0fv1.norm.value;

import java.util.Locale;
import java.util.Objects;

public record MavenArtifactCoordinate(String group, String artifact, String version) {
  public MavenArtifactCoordinate {
    requireCoordinatePart(group, "Maven group");
    requireCoordinatePart(artifact, "Maven artifact");
    Objects.requireNonNull(version, "version");
    if (version.isBlank()) throw new IllegalArgumentException("Maven version must not be blank");
    String normalized = version.toUpperCase(Locale.ROOT);
    if (version.contains("+")
        || version.contains("[")
        || version.contains("]")
        || version.contains("(")
        || version.contains(")")
        || normalized.contains("SNAPSHOT")
        || normalized.equals("LATEST")
        || normalized.equals("RELEASE")) {
      throw new IllegalArgumentException("Maven version must be fixed: " + version);
    }
  }

  public boolean isFixedVersion() {
    return true;
  }

  public String notation() {
    return group + ":" + artifact + ":" + version;
  }

  private static void requireCoordinatePart(String value, String role) {
    Objects.requireNonNull(value, role);
    if (value.isBlank() || !value.matches("[A-Za-z0-9_.-]+")) {
      throw new IllegalArgumentException(role + " is invalid: " + value);
    }
  }
}
