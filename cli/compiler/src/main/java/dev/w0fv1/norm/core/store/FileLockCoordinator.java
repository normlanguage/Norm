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

  private final ConcurrentHashMap<Path, Gate> gates = new ConcurrentHashMap<>();

  static FileLockCoordinator shared() {
    return SHARED;
  }

  <T> T withLock(Path lockFile, IoOperation<T> operation) throws IOException {
    Path key = lockFile.toAbsolutePath().normalize();
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
      Files.createDirectories(key.getParent());
      try (FileChannel channel =
          FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        FileLock fileLock = channel.lock();
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

  private static final class Gate {
    private final ReentrantLock lock = new ReentrantLock(true);
    private int users;
  }
}
