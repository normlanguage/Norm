package dev.w0fv1.norm.platform.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.FileFailure;
import dev.w0fv1.norm.execution.FileOperation;
import dev.w0fv1.norm.execution.PlatformFileException;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.TimeFailure;
import dev.w0fv1.norm.execution.TimeOperation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JdkSystemPlatformTest {
  @TempDir Path directory;

  @Test
  void readsUtf8TextFromTheHostFileSystem() throws Exception {
    Path file = directory.resolve("内容.txt");
    Files.writeString(file, "Norm 文件", StandardCharsets.UTF_8);

    String text =
        JdkSystemPlatform.standard().fileSystem().readText(file.toString(), StandardCharsets.UTF_8);

    assertEquals("Norm 文件", text);
  }

  @Test
  void resolvesRelativePathsAgainstTheInjectedWorkingDirectory() throws Exception {
    Files.writeString(directory.resolve("message.txt"), "Norm relative", StandardCharsets.UTF_8);

    String text =
        JdkSystemPlatform.builder()
            .workingDirectory(directory)
            .build()
            .fileSystem()
            .readText("message.txt", StandardCharsets.UTF_8);

    assertEquals("Norm relative", text);
  }

  @Test
  void normalizesMissingAndInvalidPaths() {
    PlatformFileException missing =
        assertThrows(
            PlatformFileException.class,
            () ->
                JdkSystemPlatform.standard()
                    .fileSystem()
                    .readText(directory.resolve("missing.txt").toString(), StandardCharsets.UTF_8));
    PlatformFileException invalid =
        assertThrows(
            PlatformFileException.class,
            () ->
                JdkSystemPlatform.standard()
                    .fileSystem()
                    .readText("invalid\0path", StandardCharsets.UTF_8));

    assertEquals(FileOperation.READ_TEXT, missing.operation());
    assertEquals(FileFailure.NOT_FOUND, missing.reason());
    assertEquals(FileOperation.READ_TEXT, invalid.operation());
    assertEquals(FileFailure.INVALID_PATH, invalid.reason());
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
}
