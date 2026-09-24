package dev.w0fv1.norm.testing;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MavenTestRepository {
  private MavenTestRepository() {}

  public static MavenArtifactCoordinate commonsLang() {
    return new MavenArtifactCoordinate(
        "org.apache.commons",
        "commons-lang3",
        System.getProperty("norm.test.commonsLangVersion", "3.20.0"));
  }

  public static Path prepare(Path destination) throws IOException {
    String configured = System.getProperty("norm.test.mavenRepository");
    if (configured == null) return destination;
    Path source = Path.of(configured).toAbsolutePath().normalize();
    if (!Files.isDirectory(source))
      throw new IOException("Maven fixture repository does not exist: " + source);
    try (var files = Files.walk(source)) {
      for (Path file :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".pom") || path.toString().endsWith(".jar"))
              .toList()) {
        Path target = destination.resolve(source.relativize(file));
        if (!Files.exists(target)) {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
    return destination;
  }
}
