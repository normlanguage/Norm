package dev.w0fv1.norm.project;

import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ProjectInputSnapshot(
    List<FileSnapshot> files, List<DirectorySnapshot> directories, List<Path> absent) {
  public ProjectInputSnapshot {
    files = files.stream().distinct().toList();
    directories = directories.stream().distinct().toList();
    absent = absent.stream().distinct().toList();
  }

  public boolean matches() {
    try {
      for (Path path : absent) if (Files.exists(path)) return false;
      for (FileSnapshot file : files) file.verify();
      for (DirectorySnapshot directory : directories) if (!directory.matches()) return false;
      return true;
    } catch (IOException missingOrChanged) {
      return false;
    }
  }
}
