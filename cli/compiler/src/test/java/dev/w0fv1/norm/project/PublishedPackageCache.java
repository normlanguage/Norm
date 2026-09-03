package dev.w0fv1.norm.project;

import java.nio.file.Path;

final class PublishedPackageCache {
  private static final Path PATH =
      Path.of("build", "published-package-cache").toAbsolutePath().normalize();

  static Path path() {
    return PATH;
  }

  private PublishedPackageCache() {}
}
