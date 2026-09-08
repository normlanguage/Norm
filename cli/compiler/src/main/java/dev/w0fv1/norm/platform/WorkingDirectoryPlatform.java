package dev.w0fv1.norm.platform;

import dev.w0fv1.norm.platform.file.FileSystem;
import dev.w0fv1.norm.platform.file.FileWriteMode;
import dev.w0fv1.norm.platform.file.PlatformByteReader;
import dev.w0fv1.norm.platform.file.PlatformByteWriter;
import dev.w0fv1.norm.platform.http.HttpTransport;
import dev.w0fv1.norm.platform.time.SystemClock;
import java.nio.file.Path;
import java.util.Objects;

public record WorkingDirectoryPlatform(SystemPlatform delegate, Path directory)
    implements SystemPlatform {
  public WorkingDirectoryPlatform {
    Objects.requireNonNull(delegate, "delegate");
    directory = directory.toAbsolutePath().normalize();
  }

  @Override
  public FileSystem fileSystem() {
    return new FileSystem() {
      @Override
      public PlatformByteReader openRead(String path) {
        return delegate.fileSystem().openRead(directory.resolve(path).normalize().toString());
      }

      @Override
      public PlatformByteWriter openWrite(String path, FileWriteMode mode) {
        return delegate
            .fileSystem()
            .openWrite(directory.resolve(path).normalize().toString(), mode);
      }
    };
  }

  @Override
  public SystemClock clock() {
    return delegate.clock();
  }

  @Override
  public HttpTransport httpTransport() {
    return delegate.httpTransport();
  }
}
