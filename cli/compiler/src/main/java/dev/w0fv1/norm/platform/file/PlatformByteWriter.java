package dev.w0fv1.norm.platform.file;

public interface PlatformByteWriter extends AutoCloseable {
  int write(byte[] source, int offset, int length);

  void flush();

  void sync(FileSyncMode mode);

  @Override
  void close();
}
