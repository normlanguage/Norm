package dev.w0fv1.norm.platform.file;

public interface FileSystem {
  PlatformByteReader openRead(String path);

  PlatformByteWriter openWrite(String path, FileWriteMode mode);
}
