package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApplicationEntryTest {
  @TempDir Path directory;

  @Test
  void selectsMainAndPreservesApplicationPrecedence() throws Exception {
    Path main = Files.writeString(directory.resolve("main.norm"), "Void main() {}");
    assertEquals(main, ApplicationEntry.resolve(directory));
    Path application = Files.writeString(directory.resolve("application.norm"), "Void main() {}");
    assertEquals(application, ApplicationEntry.resolve(directory));
    assertEquals(main, ApplicationEntry.resolve(main));
  }

  @Test
  void reportsDirectoryWithoutAnEntry() {
    assertThrows(IOException.class, () -> ApplicationEntry.resolve(directory));
  }
}
