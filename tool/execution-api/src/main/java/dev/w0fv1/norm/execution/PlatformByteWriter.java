package dev.w0fv1.norm.execution;

public interface PlatformByteWriter extends AutoCloseable {
  int write(byte[] source, int offset, int length);

  void flush();

  void sync(FileSyncMode mode);

  @Override
  void close();
}
