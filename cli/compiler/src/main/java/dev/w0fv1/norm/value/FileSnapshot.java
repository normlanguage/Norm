package dev.w0fv1.norm.value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public record FileSnapshot(Path path, Sha256Digest content) {
  public FileSnapshot {
    path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    Objects.requireNonNull(content, "content");
  }

  public static FileSnapshot capture(Path path) throws IOException {
    return new FileSnapshot(path, Sha256Digest.compute(path));
  }

  public void verify() throws IOException {
    if (!content.equals(Sha256Digest.compute(path))) {
      throw new IOException("file content changed since capture: " + path);
    }
  }

  public FileSnapshot copyTo(Path destination) throws IOException {
    Path target = destination.toAbsolutePath().normalize();
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".norm-content-", ".part");
    try {
      Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
      new FileSnapshot(temporary, content).verify();
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return new FileSnapshot(target, content);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
