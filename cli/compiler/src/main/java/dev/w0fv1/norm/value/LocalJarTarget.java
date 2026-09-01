package dev.w0fv1.norm.value;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record LocalJarTarget(String path, Optional<Sha256Digest> integrity) implements JarTarget {
  public LocalJarTarget {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(integrity, "integrity");
    path = path.replace('\\', '/');
    if (path.isBlank()
        || path.startsWith("/")
        || path.matches("^[A-Za-z]:/.*")
        || path.endsWith("/")
        || !path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
      throw new IllegalArgumentException("local JAR path must be a module-relative .jar file");
    }
    for (String segment : path.split("/", -1)) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("local JAR path must be normalized inside the module");
      }
    }
  }
}
