package dev.w0fv1.norm.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TemporaryDirectory implements AutoCloseable {
  private Path directory;
  private boolean closed;

  public Path path() throws IOException {
    if (closed) throw new IllegalStateException("Temporary directory is closed");
    if (directory == null) directory = Files.createTempDirectory("norm-build-");
    return directory;
  }

  @Override
  public void close() {
    if (closed) return;
    if (directory != null && Files.exists(directory)) {
      try (var files = Files.walk(directory)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
      } catch (IOException exception) {
        throw new UncheckedIOException("Cannot clean build workspace: " + directory, exception);
      }
    }
    closed = true;
  }
}
