package dev.w0fv1.norm.value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record DirectorySnapshot(Path root, boolean normSourcesOnly, List<FileSnapshot> files) {
  public DirectorySnapshot {
    root = root.toAbsolutePath().normalize();
    files = List.copyOf(files);
  }

  public static DirectorySnapshot capture(Path root, boolean normSourcesOnly) throws IOException {
    Path directory = root.toAbsolutePath().normalize();
    var files = new java.util.ArrayList<FileSnapshot>();
    if (Files.isDirectory(directory)) {
      try (var paths = Files.walk(directory)) {
        for (Path path :
            paths
                .filter(Files::isRegularFile)
                .filter(
                    value -> !normSourcesOnly || value.getFileName().toString().endsWith(".norm"))
                .sorted()
                .toList()) files.add(FileSnapshot.capture(path));
      }
    }
    return new DirectorySnapshot(directory, normSourcesOnly, files);
  }

  public boolean matches() throws IOException {
    return equals(capture(root, normSourcesOnly));
  }
}
