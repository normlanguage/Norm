package dev.w0fv1.norm.execution;

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
