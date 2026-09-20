package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WindowsSubsystemTest {
  @TempDir Path directory;

  @Test
  void changesOnlyTheSubsystemOfPe32AndPe32PlusImages() throws Exception {
    for (int magic : new int[] {0x10b, 0x20b}) {
      byte[] bytes = new byte[512];
      ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      data.putShort(0, (short) 0x5a4d);
      data.putInt(0x3c, 0x80);
      data.putInt(0x80, 0x4550);
      data.putShort(0x80 + 20, (short) 0xf0);
      data.putShort(0x80 + 24, (short) magic);
      data.putShort(0x80 + 24 + 68, (short) 3);
      Path image = Files.write(directory.resolve("app-" + magic + ".exe"), bytes);
      WindowsSubsystem.WINDOWED.apply(image);
      data.putShort(0x80 + 24 + 68, (short) 2);
      assertArrayEquals(bytes, Files.readAllBytes(image));
      WindowsSubsystem.CONSOLE.apply(image);
      data.putShort(0x80 + 24 + 68, (short) 3);
      assertArrayEquals(bytes, Files.readAllBytes(image));
    }
  }

  @Test
  void rejectsMalformedImagesWithoutChangingThem() throws Exception {
    for (byte[] bytes : new byte[][] {new byte[0], new byte[] {'M', 'Z'}, new byte[512]}) {
      Path image = Files.write(directory.resolve("invalid.exe"), bytes);
      assertThrows(IOException.class, () -> WindowsSubsystem.WINDOWED.apply(image));
      assertArrayEquals(bytes, Files.readAllBytes(image));
    }
  }
}
