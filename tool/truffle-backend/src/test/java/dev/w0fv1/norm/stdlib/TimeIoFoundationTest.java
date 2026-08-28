package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.execution.FileSystem;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.SystemClock;
import dev.w0fv1.norm.execution.SystemPlatform;
import dev.w0fv1.norm.execution.TimeFailure;
import dev.w0fv1.norm.execution.TimeOperation;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

final class TimeIoFoundationTest {
  @Test
  void exposesDurationInstantAndAnInjectedClock() {
    SystemPlatform platform =
        JdkSystemPlatform.builder()
            .clock(Clock.fixed(Instant.ofEpochSecond(1_700_000_000L, 123_456_789), ZoneOffset.UTC))
            .build();

    assertOutput(
        platform,
        "import std.time.Clock import std.time.Duration import std.time.Instant "
            + "import std.time.TimeException import std.time.duration "
            + "import std.time.systemClock Void main() { "
            + "Duration duration = duration(seconds: 5, nanoseconds: 7) "
            + "Clock clock = systemClock() Instant instant = clock.now() "
            + "printLine(duration.seconds()) printLine(duration.nanoseconds()) "
            + "printLine(duration == duration(seconds: 5, nanoseconds: 7)) "
            + "printLine(instant.epochSecond()) printLine(instant.nanosecond()) "
            + "try { duration(seconds: 0, nanoseconds: 1000000000) } "
            + "catch TimeException error { printLine(error.code) } }",
        "5",
        "7",
        "true",
        "1700000000",
        "123456789",
        "NORM-TIME-INVALID-DURATION");
  }

  @Test
  void exposesClockFailuresAsCatchableTimeExceptions() {
    PlatformTimeException failure =
        new PlatformTimeException(
            TimeOperation.NOW,
            TimeFailure.CLOCK_UNAVAILABLE,
            "clock unavailable",
            new IllegalStateException("clock"));
    SystemPlatform platform =
        new SystemPlatform() {
          @Override
          public FileSystem fileSystem() {
            return JdkSystemPlatform.standard().fileSystem();
          }

          @Override
          public SystemClock clock() {
            return () -> {
              throw failure;
            };
          }

          @Override
          public dev.w0fv1.norm.execution.HttpTransport httpTransport() {
            return JdkSystemPlatform.standard().httpTransport();
          }
        };

    assertOutput(
        platform,
        "import std.time.TimeException import std.time.systemClock Void main() { "
            + "try { systemClock().now() } catch TimeException error { "
            + "printLine(error.code) printLine(error.reason) } }",
        "NORM-TIME-CLOCK-UNAVAILABLE",
        "TimeFailure.ClockUnavailable");
  }

  @Test
  void providesBytesReadChunksAndTheResourceProtocol() {
    assertOutput(
        "import std.io.ByteException import std.io.Bytes import std.io.ReadChunk "
            + "import std.io.bytes "
            + "import std.io.Resource class TestResource implements Resource { "
            + "public Void close() { printLine(\"closed\") } } Void main() { "
            + "Bytes value = bytes(values: [0, 127, 255]) "
            + "Bytes middle = value.slice(start: 1, length: 2) "
            + "Array<Integer> copied = value.toArray() copied[0] = 9 "
            + "printLine(value.size()) printLine(value.at(index: 0)) "
            + "printLine(value.at(index: 2)) printLine(value == bytes(values: [0, 127, 255])) "
            + "printLine(middle.size()) printLine(middle.at(index: 0)) "
            + "printLine(middle == bytes(values: [127, 255])) "
            + "ReadChunk chunk = ReadChunk.Data(bytes: value) switch chunk { "
            + "case Data(Bytes bytes) { printLine(bytes.size()) } case Eof { printLine(\"eof\") } } "
            + "try { bytes(values: [256]) } catch ByteException error { "
            + "printLine(error.code) printLine(error.index) printLine(error.value) } "
            + "Resource resource = TestResource() resource.close() }",
        "3",
        "0",
        "255",
        "true",
        "2",
        "127",
        "true",
        "3",
        "NORM-IO-BYTE-OUT-OF-RANGE",
        "0",
        "256",
        "closed");
  }

  @Test
  void hidesPrivateRepresentationsAndTimeIntrinsics() {
    assertFalse(
        compile(
                "import std.time.Duration Void main() { "
                    + "Duration(seconds: 1, nanoseconds: 0) }")
            .isSuccess());
    assertFalse(compile("Void main() { printLine(__systemClock()) }").isSuccess());
  }

  @Test
  void usesResourcesWithoutReplacingBodyFailures() {
    assertOutput(
        "import std.core.Exception import std.io.Resource import std.io.use "
            + "class BodyFailure extends Exception { BodyFailure() { super(message: \"body\") } } "
            + "class CloseFailure extends Exception { CloseFailure() { super(message: \"close\") } } "
            + "class FailingResource implements Resource { public Void close() { "
            + "printLine(\"closed\") throw CloseFailure() } } Void main() { "
            + "try { use<Integer>(resource: FailingResource(), body: () { "
            + "throw BodyFailure() 0 }) } catch BodyFailure error { printLine(error.message) } "
            + "try { use<Integer>(resource: FailingResource(), body: () { "
            + "7 }) } catch CloseFailure error { printLine(error.message) } }",
        "closed",
        "body",
        "closed",
        "close");
  }
}
