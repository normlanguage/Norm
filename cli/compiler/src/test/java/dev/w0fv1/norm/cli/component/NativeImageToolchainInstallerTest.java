package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeImageToolchainInstallerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void installsAVerifiedArchiveOnceAndReusesIt() throws IOException {
    Path archive = temporaryDirectory.resolve("graalvm.zip");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("graalvm/bin/" + executableName()));
      output.write("@echo native-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
    }
    NativeImageDistribution distribution =
        new NativeImageDistribution(
            "windows-x64",
            "test-version",
            URI.create("https://example.invalid/graalvm.zip"),
            Sha256Digest.compute(archive),
            NativeImageDistribution.ArchiveFormat.ZIP);
    AtomicInteger downloads = new AtomicInteger();
    NativeImageToolchainInstaller installer =
        new NativeImageToolchainInstaller(
            distribution,
            temporaryDirectory.resolve("toolchains"),
            (source, destination, progress) -> {
              downloads.incrementAndGet();
              Files.copy(archive, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            });

    NativeImageToolchain first = installer.install(message -> {});
    NativeImageToolchain second = installer.install(message -> {});

    assertEquals(first, second);
    assertEquals(1, downloads.get());
    assertTrue(Files.isRegularFile(first.executable()));
  }

  @Test
  void extractsTarGzipDistributions() throws IOException {
    Path archive = temporaryDirectory.resolve("graalvm.tar.gz");
    byte[] script = "@echo native-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (var file = Files.newOutputStream(archive);
        var gzip = new GzipCompressorOutputStream(file);
        var output = new TarArchiveOutputStream(gzip)) {
      TarArchiveEntry entry = new TarArchiveEntry("graalvm/bin/" + executableName());
      entry.setSize(script.length);
      entry.setMode(0755);
      output.putArchiveEntry(entry);
      output.write(script);
      output.closeArchiveEntry();
    }
    NativeImageDistribution distribution =
        new NativeImageDistribution(
            "test-tar",
            "test-version",
            URI.create("https://example.invalid/graalvm.tar.gz"),
            Sha256Digest.compute(archive),
            NativeImageDistribution.ArchiveFormat.TAR_GZIP);
    NativeImageToolchainInstaller installer =
        new NativeImageToolchainInstaller(
            distribution,
            temporaryDirectory.resolve("tar-toolchains"),
            (source, destination, progress) ->
                Files.copy(
                    archive, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING));

    NativeImageToolchain toolchain = installer.install(message -> {});

    assertTrue(Files.isRegularFile(toolchain.executable()));
  }

  @Test
  void rejectsAnArchiveWhosePublishedIntegrityDoesNotMatch() throws IOException {
    Path archive = temporaryDirectory.resolve("invalid.zip");
    Files.writeString(archive, "not a distribution");
    NativeImageDistribution distribution =
        new NativeImageDistribution(
            "invalid",
            "test-version",
            URI.create("https://example.invalid/graalvm.zip"),
            Sha256Digest.compute("different".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            NativeImageDistribution.ArchiveFormat.ZIP);
    NativeImageToolchainInstaller installer =
        new NativeImageToolchainInstaller(
            distribution,
            temporaryDirectory.resolve("invalid-toolchains"),
            (source, destination, progress) ->
                Files.copy(
                    archive, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING));

    IOException failure = assertThrows(IOException.class, () -> installer.install(message -> {}));

    assertTrue(failure.getMessage().contains("integrity mismatch"));
  }

  private static String executableName() {
    return System.getProperty("os.name", "").startsWith("Windows")
        ? "native-image.cmd"
        : "native-image";
  }
}
