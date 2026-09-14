package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSnapshotTest {
  @TempDir Path directory;

  @Test
  void publishesVerifiedContentAcrossReadBoundaries() throws Exception {
    byte[] bytes = new byte[262_161];
    new java.util.Random(17).nextBytes(bytes);
    Path source = Files.write(directory.resolve("source.bin"), bytes);
    var expected =
        Sha256Digest.parse(
            java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    assertEquals(expected, Sha256Digest.compute(source));
    var captured = new FileSnapshot(source, expected);
    Path target = directory.resolve("published/content.bin");
    var published = captured.copyTo(target);
    assertEquals(expected, published.content());
    assertArrayEquals(bytes, Files.readAllBytes(target));
    published.verify();
  }

  @Test
  void rejectsChangedContentWithoutReplacingThePublishedFile() throws Exception {
    Path source = Files.writeString(directory.resolve("source.txt"), "original");
    var captured = FileSnapshot.capture(source);
    Path target = Files.writeString(directory.resolve("published.txt"), "previous");
    var timestamp = Files.getLastModifiedTime(source);
    Files.writeString(source, "modified");
    Files.setLastModifiedTime(source, timestamp);
    assertThrows(java.io.IOException.class, () -> captured.copyTo(target));
    assertEquals("previous", Files.readString(target));
    try (var files = Files.list(directory)) {
      assertEquals(2, files.count());
    }
  }
}
