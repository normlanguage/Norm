package dev.w0fv1.norm.build;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public enum WindowsSubsystem {
  CONSOLE(3),
  WINDOWED(2);

  private final int value;

  WindowsSubsystem(int value) {
    this.value = value;
  }

  void apply(Path executable) throws IOException {
    try (var file = new RandomAccessFile(executable.toFile(), "rw")) {
      if (file.length() < 64 || Short.reverseBytes(file.readShort()) != 0x5a4d)
        throw new IOException("Invalid Windows executable: " + executable);
      file.seek(0x3c);
      long header = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
      if (header < 64 || header + 24 + 70 > file.length())
        throw new IOException("Invalid Windows executable header: " + executable);
      file.seek(header);
      if (Integer.reverseBytes(file.readInt()) != 0x4550)
        throw new IOException("Invalid Windows PE signature: " + executable);
      file.seek(header + 20);
      int size = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
      file.seek(header + 24);
      int magic = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
      if (size < 70 || header + 24 + size > file.length() || (magic != 0x10b && magic != 0x20b))
        throw new IOException("Invalid Windows optional header: " + executable);
      file.seek(header + 24 + 68);
      file.writeShort(Short.reverseBytes((short) value));
    }
  }
}
