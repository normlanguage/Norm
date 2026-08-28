package dev.w0fv1.norm.execution;

public interface PlatformByteReader extends AutoCloseable {
  PlatformRead read(int maximumBytes);

  @Override
  void close();
}
