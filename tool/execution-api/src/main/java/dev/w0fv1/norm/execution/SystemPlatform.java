package dev.w0fv1.norm.execution;

public interface SystemPlatform {
  SystemPlatform UNAVAILABLE =
      new SystemPlatform() {
        @Override
        public FileSystem fileSystem() {
          return (path, encoding) -> {
            throw new IllegalStateException("file-system capability is unavailable");
          };
        }

        @Override
        public SystemClock clock() {
          return () -> {
            throw new IllegalStateException("clock capability is unavailable");
          };
        }
      };

  FileSystem fileSystem();

  SystemClock clock();

  static SystemPlatform unavailable() {
    return UNAVAILABLE;
  }
}
