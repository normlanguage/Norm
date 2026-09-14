package dev.w0fv1.norm.core.store;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class FileLockCoordinator {
  private static final FileLockCoordinator SHARED = new FileLockCoordinator();

  private final ConcurrentHashMap<LockKey, Gate> gates = new ConcurrentHashMap<>();
  private final java.util.Map<Path, SharedChannel> channels = new java.util.HashMap<>();

  static FileLockCoordinator shared() {
    return SHARED;
  }

  <T> T withLock(Path lockFile, IoOperation<T> operation) throws IOException {
    return withLock(new LockKey(lockFile.toAbsolutePath().normalize(), 0), operation);
  }

  <T> T withKeyLock(Path lockFile, dev.w0fv1.norm.value.Sha256Digest key, IoOperation<T> operation)
      throws IOException {
    return withLock(
        new LockKey(
            lockFile.toAbsolutePath().normalize(),
            Long.parseLong(key.value().substring(0, 15), 16)),
        operation);
  }

  private <T> T withLock(LockKey key, IoOperation<T> operation) throws IOException {
    Gate gate =
        gates.compute(
            key,
            (ignored, current) -> {
              Gate acquired = current == null ? new Gate() : current;
              acquired.users++;
              return acquired;
            });
    gate.lock.lock();
    try {
      SharedChannel borrowed;
      synchronized (channels) {
        borrowed = channels.get(key.path());
        if (borrowed == null) {
          Files.createDirectories(key.path().getParent());
          borrowed =
              new SharedChannel(
                  key.path(),
                  FileChannel.open(
                      key.path(), StandardOpenOption.CREATE, StandardOpenOption.WRITE));
          channels.put(key.path(), borrowed);
        }
        borrowed.users++;
      }
      try (var owner = borrowed) {
        FileLock fileLock = owner.channel.lock(key.position(), 1, false);
        try {
          return operation.run();
        } finally {
          fileLock.release();
        }
      }
    } finally {
      gate.lock.unlock();
      gates.computeIfPresent(
          key,
          (ignored, current) -> {
            if (current != gate) throw new IllegalStateException("file lock gate identity changed");
            current.users--;
            return current.users == 0 ? null : current;
          });
    }
  }

  @FunctionalInterface
  interface IoOperation<T> {
    T run() throws IOException;
  }

  private final class SharedChannel implements AutoCloseable {
    private final Path path;
    private final FileChannel channel;
    private int users;

    private SharedChannel(Path path, FileChannel channel) {
      this.path = path;
      this.channel = channel;
    }

    @Override
    public void close() throws IOException {
      synchronized (channels) {
        if (--users == 0) {
          channels.remove(path);
          channel.close();
        }
      }
    }
  }

  private record LockKey(Path path, long position) {}

  private static final class Gate {
    private final ReentrantLock lock = new ReentrantLock(true);
    private int users;
  }
}
