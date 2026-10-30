package dev.w0fv1.norm.cli.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SystemUpdateHostTest {
  @Test
  void handsInstallationToTheLauncherWithoutStartingAChild(@TempDir Path directory)
      throws Exception {
    Path request = directory.resolve("request");
    Path staged = Files.writeString(directory.resolve("norm-update-new.exe"), "new");
    Path target = Files.writeString(directory.resolve("norm.exe"), "old");
    new SystemUpdateHost(request).install(staged, target);
    assertEquals(staged.toString(), Files.readString(request));
    assertEquals("old", Files.readString(target));
    assertEquals("new", Files.readString(staged));
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> new SystemUpdateHost(request).install(staged, target));
  }
}
