package dev.w0fv1.norm.value;

import java.util.Objects;

public record ModuleSourceCoordinate(ModuleCoordinate module, String relativePath)
    implements Comparable<ModuleSourceCoordinate> {
  public ModuleSourceCoordinate {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(relativePath, "relativePath");
    relativePath = normalize(relativePath);
  }

  @Override
  public int compareTo(ModuleSourceCoordinate other) {
    int moduleOrder = module.compareTo(Objects.requireNonNull(other, "other").module);
    return moduleOrder != 0 ? moduleOrder : relativePath.compareTo(other.relativePath);
  }

  public String identity() {
    StringBuilder result = new StringBuilder();
    for (String segment :
        new String[] {module.name(), Integer.toString(module.version()), relativePath}) {
      result.append(segment.length()).append(':').append(segment);
    }
    return result.toString();
  }

  private static String normalize(String path) {
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    if (normalized.isBlank()) throw new IllegalArgumentException("source path must not be blank");
    for (String segment : normalized.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("source path must be normalized");
      }
    }
    return normalized;
  }
}
