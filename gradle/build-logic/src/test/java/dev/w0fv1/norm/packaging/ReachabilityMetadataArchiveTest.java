package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReachabilityMetadataArchiveTest {
  @TempDir Path directory;

  @Test
  void copiesVerifiedLocalArchive() throws Exception {
    Path source = archive();
    byte[] expected = Files.readAllBytes(source);
    Path output = directory.resolve("resources/graalvm-reachability-metadata.zip");
    Files.createDirectories(output.getParent());
    Files.writeString(output, "previous archive");

    new ReachabilityMetadataArchive(source.toUri(), digest(expected)).prepare(source, true, output);

    assertArrayEquals(expected, Files.readAllBytes(output));
  }

  @Test
  void rejectsWrongDigestWithoutReplacingPriorOutput() throws Exception {
    Path source = archive();
    Path output = directory.resolve("resources/graalvm-reachability-metadata.zip");
    Files.createDirectories(output.getParent());
    Files.writeString(output, "previous verified archive");

    assertThrows(
        IOException.class,
        () ->
            new ReachabilityMetadataArchive(source.toUri(), "0".repeat(64))
                .prepare(source, true, output));

    assertArrayEquals("previous verified archive".getBytes(), Files.readAllBytes(output));
  }

  @Test
  void rejectsOfflineBuildWithoutLocalArchive() throws Exception {
    Path source = archive();
    Path output = directory.resolve("resources/graalvm-reachability-metadata.zip");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                new ReachabilityMetadataArchive(source.toUri(), digest(Files.readAllBytes(source)))
                    .prepare(null, true, output));

    assertTrue(failure.getMessage().contains("offline"));
    assertTrue(Files.notExists(output));
  }

  private Path archive() throws IOException {
    Path source = directory.resolve("source.zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
      zip.putNextEntry(new ZipEntry("metadata/repository/example/index.json"));
      zip.write("[]".getBytes());
      zip.closeEntry();
    }
    return source;
  }

  private static String digest(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
