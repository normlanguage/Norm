package dev.w0fv1.norm.logging;

import dev.w0fv1.norm.platform.file.FileSyncMode;
import dev.w0fv1.norm.platform.file.PlatformByteWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;

public final class RollingLogFile implements PlatformByteWriter {
  private final Path root;
  private final Path run;
  private final FileChannel lease;
  private final long segmentLimit;
  private final long totalLimit;
  private final Duration retention;
  private FileChannel segment;
  private int number;

  public RollingLogFile(Path directory, long segmentLimit, long days, long totalLimit)
      throws IOException {
    if (segmentLimit < 1 || days < 1 || totalLimit < 1)
      throw new IllegalArgumentException("log retention limits must be positive");
    this.segmentLimit = segmentLimit;
    this.retention = Duration.ofDays(days);
    this.totalLimit = totalLimit;
    root = Files.createDirectories(directory);
    run = Files.createTempDirectory(root, "norm-run-");
    lease =
        FileChannel.open(
            run.resolve(".active"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try {
      lease.lock();
      prune();
    } catch (IOException | RuntimeException failure) {
      lease.close();
      throw failure;
    }
  }

  public Path directory() {
    return run;
  }

  @Override
  public synchronized int write(byte[] bytes, int offset, int length) {
    try {
      if (!lease.isOpen()) throw new IOException("log file is closed");
      if (segment != null && segment.size() > 0 && segment.size() + length > segmentLimit) {
        segment.close();
        segment = null;
        prune();
      }
      if (segment == null)
        segment =
            FileChannel.open(
                run.resolve("events-" + ++number + ".jsonl"),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
      var buffer = ByteBuffer.wrap(bytes, offset, length);
      while (buffer.hasRemaining()) segment.write(buffer);
      return length;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public void flush() {}

  @Override
  public synchronized void sync(FileSyncMode mode) {
    try {
      if (segment != null) segment.force(mode == FileSyncMode.DATA_AND_METADATA);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Override
  public synchronized void close() {
    try {
      try {
        if (segment != null) segment.close();
      } finally {
        lease.close();
        if (number == 0) {
          Files.deleteIfExists(run.resolve(".active"));
          Files.deleteIfExists(run);
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private void prune() throws IOException {
    record Segment(Path path, Instant modified, long size) {}
    var candidates = new ArrayList<Segment>();
    var reclaimed = new java.util.HashSet<Path>();
    long total = 0;
    try (var runs = Files.newDirectoryStream(root, "norm-run-*")) {
      for (Path session : runs) {
        if (!Files.isDirectory(session, LinkOption.NOFOLLOW_LINKS)) continue;
        boolean active = session.equals(run);
        var marker = session.resolve(".active");
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) continue;
        if (!active) {
          try (var channel = FileChannel.open(marker, StandardOpenOption.WRITE);
              FileLock lock = channel.tryLock()) {
            active = lock == null;
          } catch (OverlappingFileLockException locked) {
            active = true;
          } catch (java.nio.file.NoSuchFileException removed) {
            continue;
          }
        }
        try (var files = Files.newDirectoryStream(session, "events-*.jsonl")) {
          for (var file : files) {
            try {
              var attrs =
                  Files.readAttributes(
                      file,
                      java.nio.file.attribute.BasicFileAttributes.class,
                      LinkOption.NOFOLLOW_LINKS);
              if (!attrs.isRegularFile()) continue;
              total += attrs.size();
              if (!active)
                candidates.add(
                    new Segment(file, attrs.lastModifiedTime().toInstant(), attrs.size()));
            } catch (java.nio.file.NoSuchFileException removed) {
              continue;
            }
          }
        } catch (java.nio.file.NoSuchFileException removed) {
          continue;
        }
      }
    }
    candidates.sort(Comparator.comparing(Segment::modified).thenComparing(Segment::path));
    Instant cutoff = Instant.now().minus(retention);
    for (Segment candidate : candidates) {
      if (total <= totalLimit && !candidate.modified().isBefore(cutoff)) break;
      Files.deleteIfExists(candidate.path());
      total -= candidate.size();
      reclaimed.add(candidate.path().getParent());
    }
    for (Path directory : reclaimed) {
      try (var remaining = Files.newDirectoryStream(directory)) {
        boolean hasContents = false;
        for (Path path : remaining) {
          if (!path.getFileName().toString().equals(".active")) hasContents = true;
        }
        if (hasContents) continue;
      } catch (java.nio.file.NoSuchFileException removed) {
        continue;
      }
      Files.deleteIfExists(directory.resolve(".active"));
      Files.deleteIfExists(directory);
    }
  }
}
