package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.FileFailure;
import dev.w0fv1.norm.execution.FileOperation;
import dev.w0fv1.norm.execution.FileSyncMode;
import dev.w0fv1.norm.execution.FileWriteMode;
import dev.w0fv1.norm.execution.PlatformFileException;
import dev.w0fv1.norm.execution.PlatformRead;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.TimeFailure;
import dev.w0fv1.norm.execution.TimeOperation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JdkSystemPlatformTest {
  @TempDir Path directory;

  @Test
  void resolvesRelativePathsAgainstTheInjectedWorkingDirectory() throws Exception {
    Files.write(directory.resolve("message.bin"), new byte[] {7});

    var fileSystem = JdkSystemPlatform.builder().workingDirectory(directory).build().fileSystem();
    try (var reader = fileSystem.openRead("message.bin")) {
      assertEquals(7, Byte.toUnsignedInt(((PlatformRead.Data) reader.read(1)).storage()[0]));
    }
  }

  @Test
  void normalizesMissingAndInvalidPaths() {
    PlatformFileException missing =
        assertThrows(
            PlatformFileException.class,
            () ->
                JdkSystemPlatform.standard()
                    .fileSystem()
                    .openRead(directory.resolve("missing.txt").toString()));
    PlatformFileException invalid =
        assertThrows(
            PlatformFileException.class,
            () -> JdkSystemPlatform.standard().fileSystem().openRead("invalid\0path"));

    assertEquals(FileOperation.OPEN_READ, missing.operation());
    assertEquals(FileFailure.NOT_FOUND, missing.reason());
    assertEquals(FileOperation.OPEN_READ, invalid.operation());
    assertEquals(FileFailure.INVALID_PATH, invalid.reason());
  }

  @Test
  void readsOwnedByteChunksAndReportsEof() throws Exception {
    Files.write(directory.resolve("bytes.bin"), new byte[] {1, 2, 3});
    var fileSystem = JdkSystemPlatform.builder().workingDirectory(directory).build().fileSystem();
    List<Integer> values = new ArrayList<>();

    try (var reader = fileSystem.openRead("bytes.bin")) {
      PlatformRead first = reader.read(2);
      PlatformRead second = reader.read(2);
      PlatformRead end = reader.read(2);
      for (byte value : ((PlatformRead.Data) first).storage()) {
        values.add(Byte.toUnsignedInt(value));
      }
      for (int index = 0; index < ((PlatformRead.Data) second).length(); index++) {
        values.add(Byte.toUnsignedInt(((PlatformRead.Data) second).storage()[index]));
      }
      assertEquals(PlatformRead.Eof.INSTANCE, end);
    }

    assertEquals(List.of(1, 2, 3), values);
  }

  @Test
  void boundsHostReadBufferAllocations() throws Exception {
    Files.write(directory.resolve("small.bin"), new byte[] {1});
    var fileSystem = JdkSystemPlatform.builder().workingDirectory(directory).build().fileSystem();

    try (var reader = fileSystem.openRead("small.bin")) {
      PlatformRead.Data data = (PlatformRead.Data) reader.read(Integer.MAX_VALUE);

      assertEquals(64 * 1024, data.storage().length);
      assertEquals(1, data.length());
    }
  }

  @Test
  void writesAndSynchronizesARealFile() throws Exception {
    var fileSystem = JdkSystemPlatform.builder().workingDirectory(directory).build().fileSystem();

    try (var writer = fileSystem.openWrite("written.bin", FileWriteMode.CREATE_NEW)) {
      byte[] content = {4, 5, 6};
      int offset = 0;
      while (offset < content.length) {
        offset += writer.write(content, offset, content.length - offset);
      }
      writer.flush();
      writer.sync(FileSyncMode.DATA_AND_METADATA);
    }

    assertEquals(List.of(4, 5, 6), bytes(directory.resolve("written.bin")));
  }

  @Test
  void honorsReplaceAndAppendWriteModes() throws Exception {
    Files.write(directory.resolve("modes.bin"), new byte[] {1});
    var fileSystem = JdkSystemPlatform.builder().workingDirectory(directory).build().fileSystem();

    try (var writer = fileSystem.openWrite("modes.bin", FileWriteMode.REPLACE)) {
      writer.write(new byte[] {2}, 0, 1);
    }
    try (var writer = fileSystem.openWrite("modes.bin", FileWriteMode.APPEND)) {
      writer.write(new byte[] {3}, 0, 1);
    }

    assertEquals(List.of(2, 3), bytes(directory.resolve("modes.bin")));
  }

  @Test
  void preservesTypedHandleFailures() throws Exception {
    Path file = directory.resolve("closed.bin");
    Files.write(file, new byte[] {1});
    var reader =
        JdkSystemPlatform.builder()
            .workingDirectory(directory)
            .build()
            .fileSystem()
            .openRead("closed.bin");
    reader.close();

    PlatformFileException closed = assertThrows(PlatformFileException.class, () -> reader.read(1));
    PlatformFileException exists =
        assertThrows(
            PlatformFileException.class,
            () ->
                JdkSystemPlatform.builder()
                    .workingDirectory(directory)
                    .build()
                    .fileSystem()
                    .openWrite("closed.bin", FileWriteMode.CREATE_NEW));

    assertEquals(FileOperation.READ, closed.operation());
    assertEquals(FileFailure.CLOSED, closed.reason());
    assertEquals(FileOperation.OPEN_WRITE, exists.operation());
    assertEquals(FileFailure.ALREADY_EXISTS, exists.reason());
  }

  @Test
  void reportsClosedWriterOperationsPrecisely() throws Exception {
    var writer =
        JdkSystemPlatform.builder()
            .workingDirectory(directory)
            .build()
            .fileSystem()
            .openWrite("writer.bin", FileWriteMode.CREATE_NEW);
    writer.close();

    PlatformFileException write =
        assertThrows(PlatformFileException.class, () -> writer.write(new byte[] {1}, 0, 1));
    PlatformFileException flush = assertThrows(PlatformFileException.class, writer::flush);
    PlatformFileException sync =
        assertThrows(
            PlatformFileException.class, () -> writer.sync(FileSyncMode.DATA_AND_METADATA));

    assertEquals(FileOperation.WRITE, write.operation());
    assertEquals(FileOperation.FLUSH, flush.operation());
    assertEquals(FileOperation.SYNC, sync.operation());
    assertEquals(FileFailure.CLOSED, write.reason());
    assertEquals(FileFailure.CLOSED, flush.reason());
    assertEquals(FileFailure.CLOSED, sync.reason());
  }

  @Test
  void readsAnInjectedFixedClock() {
    Instant fixed = Instant.ofEpochSecond(1_700_000_000L, 123_456_789);

    var value =
        JdkSystemPlatform.builder().clock(Clock.fixed(fixed, ZoneOffset.UTC)).build().clock().now();

    assertEquals(fixed.getEpochSecond(), value.epochSecond());
    assertEquals(fixed.getNano(), value.nanosecond());
  }

  @Test
  void normalizesClockProviderFailures() {
    Clock broken =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            throw new DateTimeException("unavailable");
          }
        };

    PlatformTimeException failure =
        assertThrows(
            PlatformTimeException.class,
            () -> JdkSystemPlatform.builder().clock(broken).build().clock().now());

    assertEquals(TimeOperation.NOW, failure.operation());
    assertEquals(TimeFailure.CLOCK_UNAVAILABLE, failure.reason());
  }

  private static List<Integer> bytes(Path path) throws Exception {
    List<Integer> result = new ArrayList<>();
    for (byte value : Files.readAllBytes(path)) result.add(Byte.toUnsignedInt(value));
    return result;
  }
}
