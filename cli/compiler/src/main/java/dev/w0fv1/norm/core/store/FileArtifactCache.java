package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public final class FileArtifactCache {
  private final Path directory;
  private final int maximumEntries;
  private final long maximumBytes;

  public FileArtifactCache(Path directory, int maximumEntries, long maximumBytes)
      throws IOException {
    if (maximumEntries < 1 || maximumBytes < 129)
      throw new IllegalArgumentException("invalid artifact cache capacity");
    Files.createDirectories(directory);
    this.directory = directory.toRealPath();
    this.maximumEntries = maximumEntries;
    this.maximumBytes = maximumBytes;
  }

  public Optional<byte[]> read(Sha256Digest key) throws IOException {
    return FileLockCoordinator.shared()
        .withLock(
            directory.resolve("cache.lock"),
            () -> {
              Path file = directory.resolve(key.value() + ".bin");
              if (!Files.isRegularFile(file)) return Optional.empty();
              long size = Files.size(file);
              if (size < 128 || size > maximumBytes) return Optional.empty();
              byte[] bytes = Files.readAllBytes(file);
              if (!new String(bytes, 0, 64, StandardCharsets.US_ASCII).equals(key.value()))
                return Optional.empty();
              byte[] payload = Arrays.copyOfRange(bytes, 128, bytes.length);
              String expected = new String(bytes, 64, 64, StandardCharsets.US_ASCII);
              if (!Sha256Digest.compute(payload).value().equals(expected)) return Optional.empty();
              Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
              return Optional.of(payload);
            });
  }

  public void write(Sha256Digest key, byte[] payload) throws IOException {
    if ((long) payload.length + 128 > maximumBytes) return;
    FileLockCoordinator.shared()
        .withLock(
            directory.resolve("cache.lock"),
            () -> {
              Path destination = directory.resolve(key.value() + ".bin");
              Path temporary = Files.createTempFile(directory, "artifact-", ".tmp");
              try {
                try (var output = Files.newOutputStream(temporary)) {
                  output.write(key.value().getBytes(StandardCharsets.US_ASCII));
                  output.write(
                      Sha256Digest.compute(payload).value().getBytes(StandardCharsets.US_ASCII));
                  output.write(payload);
                }
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
              } finally {
                Files.deleteIfExists(temporary);
              }
              var entries = new ArrayList<Entry>();
              try (var files = Files.newDirectoryStream(directory, "*.bin")) {
                for (Path file : files) {
                  var attributes = Files.readAttributes(file, BasicFileAttributes.class);
                  if (attributes.isRegularFile())
                    entries.add(
                        new Entry(
                            file, attributes.size(), attributes.lastModifiedTime().toMillis()));
                }
              }
              entries.sort(Comparator.comparingLong(Entry::accessed));
              long total = entries.stream().mapToLong(Entry::bytes).sum();
              int count = entries.size();
              for (var entry : entries) {
                if (count <= maximumEntries && total <= maximumBytes) break;
                if (entry.path().equals(destination)) continue;
                Files.delete(entry.path());
                total -= entry.bytes();
                count--;
              }
              return null;
            });
  }

  private record Entry(Path path, long bytes, long accessed) {}
}
