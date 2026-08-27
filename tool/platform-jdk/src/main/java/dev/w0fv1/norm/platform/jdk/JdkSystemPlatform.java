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
  private static final JdkSystemPlatform STANDARD =
      new JdkSystemPlatform(java.time.Clock.systemUTC());
  private final FileSystem fileSystem = new JdkFileSystem();
  private final SystemClock clock;

  private JdkSystemPlatform(java.time.Clock clock) {
    this.clock = new JdkSystemClock(clock);
  }

  public static JdkSystemPlatform standard() {
    return STANDARD;
  }

  public static JdkSystemPlatform withClock(java.time.Clock clock) {
    return new JdkSystemPlatform(Objects.requireNonNull(clock, "clock"));
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

  private static final class JdkFileSystem implements FileSystem {
    @Override
    public String readText(String path, Charset encoding) {
      try {
        return Files.readString(Path.of(path), encoding);
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
}
