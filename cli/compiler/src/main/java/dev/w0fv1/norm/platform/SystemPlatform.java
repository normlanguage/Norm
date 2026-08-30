package dev.w0fv1.norm.platform;

import dev.w0fv1.norm.platform.file.FileSystem;
import dev.w0fv1.norm.platform.file.FileWriteMode;
import dev.w0fv1.norm.platform.file.PlatformByteReader;
import dev.w0fv1.norm.platform.file.PlatformByteWriter;
import dev.w0fv1.norm.platform.http.HttpTransport;
import dev.w0fv1.norm.platform.time.SystemClock;

public interface SystemPlatform {
  SystemPlatform UNAVAILABLE =
      new SystemPlatform() {
        @Override
        public FileSystem fileSystem() {
          return new FileSystem() {
            @Override
            public PlatformByteReader openRead(String path) {
              throw unavailable();
            }

            @Override
            public PlatformByteWriter openWrite(String path, FileWriteMode mode) {
              throw unavailable();
            }

            private IllegalStateException unavailable() {
              return new IllegalStateException("file-system capability is unavailable");
            }
          };
        }

        @Override
        public SystemClock clock() {
          return () -> {
            throw new IllegalStateException("clock capability is unavailable");
          };
        }

        @Override
        public HttpTransport httpTransport() {
          return (request, control) -> {
            throw new IllegalStateException("http capability is unavailable");
          };
        }
      };

  FileSystem fileSystem();

  SystemClock clock();

  HttpTransport httpTransport();

  static SystemPlatform unavailable() {
    return UNAVAILABLE;
  }
}
