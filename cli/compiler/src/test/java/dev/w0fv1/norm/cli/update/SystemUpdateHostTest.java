package dev.w0fv1.norm.cli.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class SystemUpdateHostTest {
  @Test
  @EnabledOnOs(OS.WINDOWS)
  void replacesOnlyAfterTheOwningProcessExits(@TempDir Path temporary) throws Exception {
    Path directory = Files.createDirectories(temporary.resolve("install's directory"));
    Path staged = Files.writeString(directory.resolve("new.exe"), "new launcher");
    Path target = Files.writeString(directory.resolve("norm.exe"), "old launcher");
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                UpdateInstallerProcess.class.getName(),
                staged.toString(),
                target.toString())
            .redirectErrorStream(true)
            .start();
    try {
      try (var reader = process.inputReader()) {
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> assertEquals("scheduled", reader.readLine()));
        assertEquals("old launcher", Files.readString(target));
        assertTrue(Files.exists(staged));
        process.getOutputStream().write('\n');
        process.getOutputStream().flush();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertTimeoutPreemptively(
            Duration.ofSeconds(10),
            () -> {
              while (Files.exists(staged)) Thread.sleep(50);
            });
        assertEquals("new launcher", Files.readString(target));
      }
    } finally {
      process.destroyForcibly();
    }
  }
}
