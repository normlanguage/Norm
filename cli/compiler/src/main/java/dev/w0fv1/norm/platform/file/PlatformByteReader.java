package dev.w0fv1.norm.platform.file;

import dev.w0fv1.norm.platform.PlatformRead;

public interface PlatformByteReader extends AutoCloseable {
  PlatformRead read(int maximumBytes);

  @Override
  void close();
}
