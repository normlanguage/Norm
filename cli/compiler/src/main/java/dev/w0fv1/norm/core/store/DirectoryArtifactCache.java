package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

public final class DirectoryArtifactCache {
  private final Path directory;
  private final int maximumEntries;
  private final long maximumBytes;

  public DirectoryArtifactCache(Path directory, int maximumEntries, long maximumBytes)
      throws IOException {
    if (maximumEntries < 1 || maximumBytes < 1)
      throw new IllegalArgumentException("invalid directory cache capacity");
    this.directory = Files.createDirectories(directory).toRealPath();
    this.maximumEntries = maximumEntries;
    this.maximumBytes = maximumBytes;
  }

  public Lease acquire(Sha256Digest key, Validator validator, Producer producer)
      throws IOException {
    return FileLockCoordinator.shared()
        .withKeyLock(
            directory.resolve("production.lock"),
            key,
            () -> {
              var candidates =
                  FileLockCoordinator.shared()
                      .withLock(
                          directory.resolve("cache.lock"),
                          () -> {
                            var found = new ArrayList<Path>();
                            try (var entries =
                                Files.newDirectoryStream(directory, key.value() + "-*")) {
                              for (Path candidate : entries) {
                                if (Files.isRegularFile(candidate.resolve("size")))
                                  found.add(candidate);
                              }
                            }
                            return found;
                          });
              for (Path candidate : candidates) {
                var lease =
                    FileLockCoordinator.shared()
                        .withLock(
                            directory.resolve("cache.lock"),
                            () ->
                                Files.isRegularFile(candidate.resolve("size"))
                                    ? lease(candidate)
                                    : null);
                if (lease == null) continue;
                boolean retained = false;
                try {
                  boolean valid;
                  try {
                    valid = validator.matches(lease.path());
                  } catch (IOException missingOrCorrupt) {
                    valid = false;
                  }
                  if (valid) {
                    FileLockCoordinator.shared()
                        .withLock(
                            directory.resolve("cache.lock"),
                            () -> {
                              Files.setLastModifiedTime(
                                  candidate, FileTime.fromMillis(System.currentTimeMillis()));
                              prune();
                              return null;
                            });
                    retained = true;
                    return lease;
                  }
                } finally {
                  if (!retained) {
                    lease.close();
                    FileLockCoordinator.shared()
                        .withLock(
                            directory.resolve("cache.lock"),
                            () -> {
                              if (Files.isDirectory(candidate)) removeIfUnused(candidate);
                              return null;
                            });
                  }
                }
              }
              var lease =
                  FileLockCoordinator.shared()
                      .withLock(
                          directory.resolve("cache.lock"),
                          () -> {
                            Path candidate =
                                Files.createDirectory(
                                    directory.resolve(key.value() + "-" + UUID.randomUUID()));
                            try {
                              Files.createDirectory(candidate.resolve("content"));
                              return lease(candidate);
                            } catch (IOException | RuntimeException failure) {
                              removeIfUnused(candidate);
                              throw failure;
                            }
                          });
              boolean retained = false;
              Path candidate = lease.path().getParent();
              try {
                producer.write(lease.path());
                long size = 0;
                try (var files = Files.walk(lease.path())) {
                  for (Path file : files.filter(Files::isRegularFile).toList())
                    size += Files.size(file);
                }
                Files.writeString(candidate.resolve("size.pending"), Long.toString(size));
                FileLockCoordinator.shared()
                    .withLock(
                        directory.resolve("cache.lock"),
                        () -> {
                          Files.move(
                              candidate.resolve("size.pending"),
                              candidate.resolve("size"),
                              StandardCopyOption.ATOMIC_MOVE);
                          Files.setLastModifiedTime(
                              candidate, FileTime.fromMillis(System.currentTimeMillis()));
                          prune();
                          return null;
                        });
                retained = true;
                return lease;
              } finally {
                if (!retained) lease.close();
              }
            });
  }

  private Lease lease(Path candidate) throws IOException {
    activeLeases(candidate);
    Path marker = Files.createTempFile(candidate, "lease-", ".lock");
    FileChannel channel = FileChannel.open(marker, StandardOpenOption.WRITE);
    try {
      return new Lease(candidate.resolve("content"), marker, channel, channel.lock());
    } catch (IOException | RuntimeException failure) {
      channel.close();
      Files.deleteIfExists(marker);
      throw failure;
    }
  }

  private void prune() throws IOException {
    var entries = new ArrayList<Entry>();
    try (var paths = Files.newDirectoryStream(directory)) {
      for (Path path : paths) {
        if (!Files.isDirectory(path)) continue;
        long size;
        try {
          size = Long.parseLong(Files.readString(path.resolve("size")));
          if (size < 0) throw new NumberFormatException("negative directory size");
        } catch (IOException | NumberFormatException invalid) {
          removeIfUnused(path);
          continue;
        }
        entries.add(new Entry(path, size, Files.getLastModifiedTime(path).toMillis()));
      }
    }
    entries.sort(Comparator.comparingLong(Entry::accessed));
    long bytes = entries.stream().mapToLong(Entry::bytes).sum();
    int count = entries.size();
    for (var entry : entries) {
      if (count <= maximumEntries && bytes <= maximumBytes) break;
      if (removeIfUnused(entry.path())) {
        bytes -= entry.bytes();
        count--;
      }
    }
  }

  private boolean removeIfUnused(Path candidate) throws IOException {
    if (!candidate.toAbsolutePath().normalize().getParent().equals(directory))
      throw new IOException("artifact directory is outside its cache");
    if (activeLeases(candidate)) return false;
    try (var files = Files.walk(candidate)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
    }
    return true;
  }

  private boolean activeLeases(Path candidate) throws IOException {
    boolean active = false;
    try (var markers = Files.newDirectoryStream(candidate, "lease-*.lock")) {
      for (Path marker : markers) {
        try (var channel = FileChannel.open(marker, StandardOpenOption.WRITE)) {
          try (var lock = channel.tryLock()) {
            if (lock == null) {
              active = true;
              continue;
            }
          } catch (OverlappingFileLockException activeInThisProcess) {
            active = true;
            continue;
          }
        }
        Files.delete(marker);
      }
    }
    return active;
  }

  @FunctionalInterface
  public interface Validator {
    boolean matches(Path directory) throws IOException;
  }

  @FunctionalInterface
  public interface Producer {
    void write(Path directory) throws IOException;
  }

  public final class Lease implements AutoCloseable {
    private final Path path;
    private final Path marker;
    private final FileChannel channel;
    private final FileLock lock;

    private Lease(Path path, Path marker, FileChannel channel, FileLock lock) {
      this.path = path;
      this.marker = marker;
      this.channel = channel;
      this.lock = lock;
    }

    public Path path() {
      if (!channel.isOpen()) throw new IllegalStateException("artifact lease is closed");
      return path;
    }

    @Override
    public void close() throws IOException {
      FileLockCoordinator.shared()
          .withLock(
              directory.resolve("cache.lock"),
              () -> {
                if (channel.isOpen()) {
                  try {
                    lock.release();
                  } finally {
                    channel.close();
                  }
                  Files.deleteIfExists(marker);
                  prune();
                }
                return null;
              });
    }
  }

  private record Entry(Path path, long bytes, long accessed) {}
}
