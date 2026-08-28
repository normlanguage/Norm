package dev.w0fv1.norm.execution;

import java.util.Objects;

public sealed interface PlatformRead permits PlatformRead.Data, PlatformRead.Eof {
  record Data(byte[] storage, int length) implements PlatformRead {
    public Data {
      Objects.requireNonNull(storage, "storage");
      if (length < 1 || length > storage.length) {
        throw new IllegalArgumentException("data length must be within its storage");
      }
    }
  }

  enum Eof implements PlatformRead {
    INSTANCE
  }
}
