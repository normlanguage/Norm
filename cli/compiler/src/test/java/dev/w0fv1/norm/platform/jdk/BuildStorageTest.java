package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.utils.TemporaryDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildStorageTest {
  @TempDir Path directory;

  @Test
  void failedPublicationPreservesPreviousOutputAndLeavesNoParts() throws Exception {
    Path output = Files.writeString(directory.resolve("app.exe"), "previous");
    assertThrows(
        IOException.class, () -> FilePublication.publish(directory.resolve("missing"), output));
    assertEquals("previous", Files.readString(output));
    try (var files = Files.list(directory)) {
      assertEquals(java.util.List.of(output), files.toList());
    }
    try (var workspace = new TemporaryDirectory()) {
      Path source = Files.writeString(workspace.path().resolve("app.exe"), "next");
      FilePublication.publish(source, output);
      assertEquals("next", Files.readString(output));
    }
  }

  @Test
  void workspacesAreIsolatedAndCleanedWhenWorkFails() throws Exception {
    Path first;
    Path second;
    try (var one = new TemporaryDirectory();
        var two = new TemporaryDirectory()) {
      first = one.path();
      second = two.path();
      assertNotEquals(first, second);
      assertThrows(
          IOException.class,
          () -> {
            try (one) {
              Files.writeString(first.resolve("intermediate"), "generated");
              throw new IOException("compile failed");
            }
          });
      assertFalse(Files.exists(first));
      assertTrue(Files.isDirectory(second));
      assertThrows(IllegalStateException.class, one::path);
    }
    assertFalse(Files.exists(second));
  }
}
