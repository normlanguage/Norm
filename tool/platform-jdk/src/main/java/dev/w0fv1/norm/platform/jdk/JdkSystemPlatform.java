package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.execution.FileFailure;
import dev.w0fv1.norm.execution.FileOperation;
import dev.w0fv1.norm.execution.FileSyncMode;
import dev.w0fv1.norm.execution.FileSystem;
import dev.w0fv1.norm.execution.FileWriteMode;
import dev.w0fv1.norm.execution.HttpTransport;
import dev.w0fv1.norm.execution.PlatformByteReader;
import dev.w0fv1.norm.execution.PlatformByteWriter;
import dev.w0fv1.norm.execution.PlatformFileException;
import dev.w0fv1.norm.execution.PlatformInstant;
import dev.w0fv1.norm.execution.PlatformRead;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.SystemClock;
import dev.w0fv1.norm.execution.SystemPlatform;
import dev.w0fv1.norm.execution.TimeFailure;
import dev.w0fv1.norm.execution.TimeOperation;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

public final class JdkSystemPlatform implements SystemPlatform {
  private final FileSystem fileSystem;
  private final SystemClock clock;
  private final HttpTransport httpTransport;

  private JdkSystemPlatform(Builder builder) {
    fileSystem = new JdkFileSystem(builder.workingDirectory);
    clock = new JdkSystemClock(builder.clock);
    httpTransport = new JdkHttpTransport(builder.httpClient);
  }

  public static JdkSystemPlatform standard() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public FileSystem fileSystem() {
    return fileSystem;
  }

  @Override
  public SystemClock clock() {
    return clock;
  }

  @Override
  public HttpTransport httpTransport() {
    return httpTransport;
  }

  private record JdkSystemClock(java.time.Clock clock) implements SystemClock {
    private JdkSystemClock {
      Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlatformInstant now() {
      try {
        Instant instant = clock.instant();
        return new PlatformInstant(instant.getEpochSecond(), instant.getNano());
      } catch (DateTimeException exception) {
        throw new PlatformTimeException(
            TimeOperation.NOW,
            TimeFailure.CLOCK_UNAVAILABLE,
            Objects.requireNonNullElse(
                exception.getMessage(), TimeFailure.CLOCK_UNAVAILABLE.name()),
            exception);
      }
    }
  }

  private record JdkFileSystem(Path workingDirectory) implements FileSystem {
    private JdkFileSystem {
      workingDirectory =
          Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
    }

    @Override
    public PlatformByteReader openRead(String path) {
      try {
        return new JdkFileReader(path, FileChannel.open(resolve(path), StandardOpenOption.READ));
      } catch (InvalidPathException exception) {
        throw failure(FileOperation.OPEN_READ, FileFailure.INVALID_PATH, path, exception);
      } catch (SecurityException exception) {
        throw failure(FileOperation.OPEN_READ, FileFailure.PERMISSION_DENIED, path, exception);
      } catch (IOException exception) {
        throw failure(FileOperation.OPEN_READ, path, exception);
      }
    }

    @Override
    public PlatformByteWriter openWrite(String path, FileWriteMode mode) {
      Objects.requireNonNull(mode, "mode");
      try {
        FileChannel channel =
            switch (mode) {
              case CREATE_NEW ->
                  FileChannel.open(
                      resolve(path), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
              case REPLACE ->
                  FileChannel.open(
                      resolve(path),
                      StandardOpenOption.WRITE,
                      StandardOpenOption.CREATE,
                      StandardOpenOption.TRUNCATE_EXISTING);
              case APPEND ->
                  FileChannel.open(
                      resolve(path),
                      StandardOpenOption.WRITE,
                      StandardOpenOption.CREATE,
                      StandardOpenOption.APPEND);
            };
        return new JdkFileWriter(path, channel);
      } catch (InvalidPathException exception) {
        throw failure(FileOperation.OPEN_WRITE, FileFailure.INVALID_PATH, path, exception);
      } catch (SecurityException exception) {
        throw failure(FileOperation.OPEN_WRITE, FileFailure.PERMISSION_DENIED, path, exception);
      } catch (IOException exception) {
        throw failure(FileOperation.OPEN_WRITE, path, exception);
      }
    }

    private Path resolve(String path) {
      Path target = Path.of(path);
      return target.isAbsolute()
          ? target.normalize()
          : workingDirectory.resolve(target).normalize();
    }

    private static PlatformFileException failure(
        FileOperation operation, String path, Throwable exception) {
      FileFailure reason =
          switch (exception) {
            case InvalidPathException ignored -> FileFailure.INVALID_PATH;
            case NoSuchFileException ignored -> FileFailure.NOT_FOUND;
            case AccessDeniedException ignored -> FileFailure.PERMISSION_DENIED;
            case FileAlreadyExistsException ignored -> FileFailure.ALREADY_EXISTS;
            case ClosedChannelException ignored -> FileFailure.CLOSED;
            default -> FileFailure.IO;
          };
      return failure(operation, reason, path, exception);
    }

    private static PlatformFileException failure(
        FileOperation operation, FileFailure reason, String path, Throwable exception) {
      return new PlatformFileException(
          operation,
          reason,
          path,
          Objects.requireNonNullElse(exception.getMessage(), reason.name()),
          exception);
    }
  }

  private record JdkFileReader(String path, FileChannel channel) implements PlatformByteReader {
    private static final int PREFERRED_CHUNK_BYTES = 64 * 1024;

    private JdkFileReader {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(channel, "channel");
    }

    @Override
    public PlatformRead read(int maximumBytes) {
      if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
      byte[] storage = new byte[Math.min(maximumBytes, PREFERRED_CHUNK_BYTES)];
      try {
        int length = channel.read(ByteBuffer.wrap(storage));
        return length < 0 ? PlatformRead.Eof.INSTANCE : new PlatformRead.Data(storage, length);
      } catch (IOException exception) {
        throw JdkFileSystem.failure(FileOperation.READ, path, exception);
      }
    }

    @Override
    public void close() {
      try {
        channel.close();
      } catch (IOException exception) {
        throw JdkFileSystem.failure(FileOperation.CLOSE, path, exception);
      }
    }
  }

  private record JdkFileWriter(String path, FileChannel channel) implements PlatformByteWriter {
    private JdkFileWriter {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(channel, "channel");
    }

    @Override
    public int write(byte[] source, int offset, int length) {
      Objects.checkFromIndexSize(offset, length, source.length);
      if (length == 0) return 0;
      try {
        return channel.write(ByteBuffer.wrap(source, offset, length));
      } catch (IOException exception) {
        throw JdkFileSystem.failure(FileOperation.WRITE, path, exception);
      }
    }

    @Override
    public void flush() {
      if (!channel.isOpen()) {
        throw JdkFileSystem.failure(FileOperation.FLUSH, path, new ClosedChannelException());
      }
    }

    @Override
    public void sync(FileSyncMode mode) {
      Objects.requireNonNull(mode, "mode");
      try {
        channel.force(mode == FileSyncMode.DATA_AND_METADATA);
      } catch (IOException exception) {
        throw JdkFileSystem.failure(FileOperation.SYNC, path, exception);
      }
    }

    @Override
    public void close() {
      try {
        channel.close();
      } catch (IOException exception) {
        throw JdkFileSystem.failure(FileOperation.CLOSE, path, exception);
      }
    }
  }

  public static final class Builder {
    private Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    private java.time.Clock clock = java.time.Clock.systemUTC();
    private HttpClient httpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private Builder() {}

    public Builder workingDirectory(Path value) {
      workingDirectory = Objects.requireNonNull(value, "value").toAbsolutePath().normalize();
      return this;
    }

    public Builder clock(java.time.Clock value) {
      clock = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder httpClient(HttpClient value) {
      httpClient = Objects.requireNonNull(value, "value");
      return this;
    }

    public JdkSystemPlatform build() {
      return new JdkSystemPlatform(this);
    }
  }
}
