package dev.w0fv1.norm.pyrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PythonRuntimeInstallerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void verifiesExtractsAndReusesRuntime() throws IOException {
    Path archive = temporaryDirectory.resolve("python.tar.gz");
    byte[] executable = "python".getBytes(StandardCharsets.UTF_8);
    try (var file = Files.newOutputStream(archive);
        var gzip = new GZIPOutputStream(file);
        var output = new TarArchiveOutputStream(gzip)) {
      TarArchiveEntry target = new TarArchiveEntry("python/bin/python3.14");
      target.setSize(executable.length);
      target.setMode(0755);
      output.putArchiveEntry(target);
      output.write(executable);
      output.closeArchiveEntry();
      TarArchiveEntry entry = new TarArchiveEntry("python/bin/python3", TarConstants.LF_SYMLINK);
      entry.setLinkName("python3.14");
      output.putArchiveEntry(entry);
      output.closeArchiveEntry();
    }
    PythonRuntimeDistribution distribution =
        new PythonRuntimeDistribution(
            "test",
            PythonRuntimePlatform.LINUX_X64,
            URI.create("https://example.invalid/python"),
            Sha256Digest.compute(archive),
            "python/bin/python3");
    AtomicInteger downloads = new AtomicInteger();
    PythonRuntimeInstaller installer =
        new PythonRuntimeInstaller(
            distribution,
            temporaryDirectory.resolve("runtimes"),
            (source, destination, progress) -> {
              downloads.incrementAndGet();
              Files.copy(archive, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            });

    Path first = installer.install(message -> {});
    Path second = installer.install(message -> {});

    assertEquals(first, second);
    assertEquals(1, downloads.get());
    assertTrue(Files.isRegularFile(first));
    assertEquals("python", Files.readString(first));
  }

  @Test
  void rejectsIntegrityMismatchBeforeExtraction() throws IOException {
    Path archive = temporaryDirectory.resolve("invalid.tar.gz");
    Files.writeString(archive, "invalid");
    PythonRuntimeDistribution distribution =
        new PythonRuntimeDistribution(
            "test",
            PythonRuntimePlatform.LINUX_X64,
            URI.create("https://example.invalid/python"),
            Sha256Digest.compute("different".getBytes(StandardCharsets.UTF_8)),
            "python/bin/python3");
    PythonRuntimeInstaller installer =
        new PythonRuntimeInstaller(
            distribution,
            temporaryDirectory.resolve("runtimes"),
            (source, destination, progress) ->
                Files.copy(
                    archive, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING));

    IOException failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class, () -> installer.install(message -> {}));

    assertTrue(failure.getMessage().contains("integrity mismatch"));
  }
}\n