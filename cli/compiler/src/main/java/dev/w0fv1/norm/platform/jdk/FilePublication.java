package dev.w0fv1.norm.platform.jdk;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FilePublication {
  public static void publish(Path source, Path destination) throws IOException {
    Path target = destination.toAbsolutePath().normalize();
    Files.createDirectories(target.getParent());
    Path pending = Files.createTempFile(target.getParent(), ".norm-publish-", ".part");
    try {
      Files.copy(
          source, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      try {
        Files.move(
            pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(pending);
    }
  }

  private FilePublication() {}
}
