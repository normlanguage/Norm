package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimePayloadArchiveTest {
  @TempDir Path directory;

  @Test
  void archivesOnlyTheRuntimeTreeInStableOrderWithMatchingDigest() throws Exception {
    Path runtime = directory.resolve("norm-runtime");
    Files.createDirectories(runtime.resolve("lib"));
    Files.createDirectories(runtime.resolve("bin"));
    Files.writeString(runtime.resolve("lib/compiler.jar"), "compiler");
    Files.writeString(runtime.resolve("bin/norm.bat"), "launcher");
    Files.writeString(runtime.resolve("LICENSE"), "license");
    Files.writeString(directory.resolve("outside.txt"), "outside");
    Path zip = directory.resolve("output/norm-runtime.zip");
    Path digest = directory.resolve("output/norm-runtime.sha256");

    RuntimePayloadArchive.write(runtime, zip, digest);
    byte[] first = Files.readAllBytes(zip);
    try (ZipFile archive = new ZipFile(zip.toFile())) {
      assertEquals(
          List.of("LICENSE", "bin/norm.bat", "lib/compiler.jar"),
          archive.stream().map(entry -> entry.getName()).toList());
      assertEquals(
          "compiler",
          new String(archive.getInputStream(archive.getEntry("lib/compiler.jar")).readAllBytes()));
    }
    assertEquals(
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first)),
        Files.readString(digest));

    Files.setLastModifiedTime(runtime.resolve("lib/compiler.jar"), FileTime.from(Instant.now()));
    RuntimePayloadArchive.write(runtime, zip, digest);
    assertArrayEquals(first, Files.readAllBytes(zip));
  }

  @Test
  void missingRuntimeDoesNotReplacePreviousArchive() throws Exception {
    Path zip = directory.resolve("norm-runtime.zip");
    Path digest = directory.resolve("norm-runtime.sha256");
    Files.writeString(zip, "prior archive");
    Files.writeString(digest, "prior digest");

    assertThrows(
        IOException.class,
        () -> RuntimePayloadArchive.write(directory.resolve("missing"), zip, digest));

    assertEquals("prior archive", Files.readString(zip));
    assertEquals("prior digest", Files.readString(digest));
  }
}
