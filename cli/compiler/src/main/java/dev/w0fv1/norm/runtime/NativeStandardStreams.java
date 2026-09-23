package dev.w0fv1.norm.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

final class NativeStandardStreams {
  private NativeStandardStreams() {}

  static InputStream input() {
    if (ImageInfo.inImageRuntimeCode() && Platform.includedIn(Platform.WINDOWS.class)) {
      PointerBase handle = Windows.GetStdHandle(-10);
      CIntPointer mode = StackValue.get(CIntPointer.class);
      if (Windows.GetConsoleMode(handle, mode) != 0) return new WindowsInput(handle);
    }
    return System.in;
  }

  @Platforms(Platform.WINDOWS.class)
  private static final class WindowsInput extends InputStream {
    private final PointerBase handle;
    private byte[] content = new byte[0];
    private int position;
    private boolean eof;

    private WindowsInput(PointerBase handle) {
      this.handle = handle;
    }

    @Override
    public synchronized int read() throws IOException {
      byte[] value = new byte[1];
      return read(value, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(value[0]);
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, buffer.length);
      if (length == 0) return 0;
      if (position == content.length && !eof) {
        char[] chars = new char[4096];
        CIntPointer count = StackValue.get(CIntPointer.class);
        try (var pinned = PinnedObject.create(chars)) {
          if (Windows.ReadConsoleW(
                  handle,
                  pinned.addressOfArrayElement(0),
                  chars.length,
                  count,
                  WordFactory.nullPointer())
              == 0) throw new IOException("Windows console read failed: " + Windows.GetLastError());
        }
        int size = count.read();
        if (size < 0 || size > chars.length) throw new IOException("Invalid console read count");
        eof = size == 0 || chars[0] == 26;
        content = eof ? new byte[0] : new String(chars, 0, size).getBytes(StandardCharsets.UTF_8);
        position = 0;
      }
      if (eof) return -1;
      int size = Math.min(length, content.length - position);
      System.arraycopy(content, position, buffer, offset, size);
      position += size;
      return size;
    }

    @Override
    public void close() {}
  }

  static PrintWriter output() {
    return writer(System.out, -11);
  }

  static PrintWriter error() {
    return writer(System.err, -12);
  }

  private static PrintWriter writer(PrintStream stream, int descriptor) {
    if (ImageInfo.inImageRuntimeCode() && Platform.includedIn(Platform.WINDOWS.class)) {
      PointerBase handle = Windows.GetStdHandle(descriptor);
      CIntPointer mode = StackValue.get(CIntPointer.class);
      if (Windows.GetConsoleMode(handle, mode) != 0) {
        return new PrintWriter(new WindowsWriter(handle), true);
      }
    }
    return new PrintWriter(stream, true, StandardCharsets.UTF_8);
  }

  @Platforms(Platform.WINDOWS.class)
  private static final class WindowsWriter extends Writer {
    private final PointerBase handle;

    private WindowsWriter(PointerBase handle) {
      this.handle = handle;
    }

    @Override
    public void write(char[] buffer, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, buffer.length);
      synchronized (lock) {
        CIntPointer written = StackValue.get(CIntPointer.class);
        try (var pinned = PinnedObject.create(buffer)) {
          while (length > 0) {
            int count = Math.min(length, 16384);
            if (Windows.WriteConsoleW(
                    handle,
                    pinned.addressOfArrayElement(offset),
                    count,
                    written,
                    WordFactory.nullPointer())
                == 0) {
              throw new IOException("Windows console write failed: " + Windows.GetLastError());
            }
            int completed = written.read();
            if (completed <= 0 || completed > count)
              throw new IOException("Windows console write made no progress");
            offset += completed;
            length -= completed;
          }
        }
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  @Platforms(Platform.WINDOWS.class)
  @CContext(Windows.Headers.class)
  private static final class Windows {
    public static final class Headers implements CContext.Directives {
      @Override
      public List<String> getHeaderFiles() {
        return List.of("<windows.h>");
      }
    }

    @CFunction
    static native PointerBase GetStdHandle(int descriptor);

    @CFunction
    static native int GetConsoleMode(PointerBase handle, CIntPointer mode);

    @CFunction
    static native int ReadConsoleW(
        PointerBase handle, PointerBase buffer, int length, CIntPointer read, PointerBase control);

    @CFunction
    static native int WriteConsoleW(
        PointerBase handle,
        PointerBase buffer,
        int length,
        CIntPointer written,
        PointerBase reserved);

    @CFunction
    static native int GetLastError();
  }
}
