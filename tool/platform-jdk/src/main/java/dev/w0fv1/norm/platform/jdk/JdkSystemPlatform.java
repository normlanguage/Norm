package dev.w0fv1.norm.platform.jdk;

import dev.w0fv1.norm.execution.FileFailure;
import dev.w0fv1.norm.execution.FileOperation;
import dev.w0fv1.norm.execution.FileSystem;
import dev.w0fv1.norm.execution.PlatformFileException;
import dev.w0fv1.norm.execution.PlatformInstant;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.SystemClock;
import dev.w0fv1.norm.execution.SystemPlatform;
import dev.w0fv1.norm.execution.TimeFailure;
import dev.w0fv1.norm.execution.TimeOperation;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

public final class JdkSystemPlatform implements SystemPlatform {
  private static final JdkSystemPlatform STANDARD = builder().build();
  private final FileSystem fileSystem;
  private final SystemClock clock;

  private JdkSystemPlatform(Builder builder) {
    fileSystem = new JdkFileSystem(builder.workingDirectory);
    clock = new JdkSystemClock(builder.clock);
  }

  public static JdkSystemPlatform standard() {
    return STANDARD;
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
    public String readText(String path, Charset encoding) {
      try {
        Path target = Path.of(path);
        return Files.readString(
            target.isAbsolute() ? target : workingDirectory.resolve(target).normalize(), encoding);
      } catch (InvalidPathException exception) {
        throw failure(FileFailure.INVALID_PATH, path, exception);
      } catch (NoSuchFileException exception) {
        throw failure(FileFailure.NOT_FOUND, path, exception);
      } catch (AccessDeniedException exception) {
        throw failure(FileFailure.PERMISSION_DENIED, path, exception);
      } catch (IOException exception) {
        throw failure(FileFailure.IO, path, exception);
      }
    }

    private static PlatformFileException failure(
        FileFailure reason, String path, Throwable exception) {
      return new PlatformFileException(
          FileOperation.READ_TEXT,
          reason,
          path,
          Objects.requireNonNullElse(exception.getMessage(), reason.name()),
          exception);
    }
  }

  public static final class Builder {
    private Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    private java.time.Clock clock = java.time.Clock.systemUTC();

    private Builder() {}

    public Builder workingDirectory(Path value) {
      workingDirectory = Objects.requireNonNull(value, "value").toAbsolutePath().normalize();
      return this;
    }

    public Builder clock(java.time.Clock value) {
      clock = Objects.requireNonNull(value, "value");
      return this;
    }

    public JdkSystemPlatform build() {
      return new JdkSystemPlatform(this);
    }
  }
}
