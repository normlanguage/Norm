package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.platform.PlatformRead;
import dev.w0fv1.norm.platform.SystemPlatform;
import dev.w0fv1.norm.platform.file.FileFailure;
import dev.w0fv1.norm.platform.file.FileOperation;
import dev.w0fv1.norm.platform.file.FileSystem;
import dev.w0fv1.norm.platform.file.FileWriteMode;
import dev.w0fv1.norm.platform.file.PlatformByteReader;
import dev.w0fv1.norm.platform.file.PlatformByteWriter;
import dev.w0fv1.norm.platform.file.PlatformFileException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSystemTest {
  @TempDir Path directory;

  @Test
  void readsUtf8TextFromARealFile() throws Exception {
    Path file = directory.resolve("message.txt");
    Files.writeString(file, "Norm 系统层", StandardCharsets.UTF_8);

    assertOutput(
        "import std.filesystem.Path import std.filesystem.readText "
            + "import std.io.TextEncoding Void main() { "
            + "printLine(readText(path: Path(value: \""
            + literal(file)
            + "\"), encoding: TextEncoding.Utf8, maximumBytes: 64)) }",
        "Norm 系统层");
  }

  @Test
  void exposesMissingFilesAsCatchableFileExceptions() {
    Path file = directory.resolve("missing.txt");

    assertOutput(
        "import std.filesystem.FileException import std.filesystem.Path "
            + "import std.filesystem.readText import std.io.TextEncoding Void main() { "
            + "try { readText(path: Path(value: \""
            + literal(file)
            + "\"), encoding: TextEncoding.Utf8, maximumBytes: 64) } "
            + "catch FileException error { printLine(error.code) printLine(error.reason) } }",
        "NORM-FS-NOT-FOUND",
        "FileFailure.NotFound");
  }

  @Test
  void streamsBytesThroughRealScopedFileResources() {
    Path file = directory.resolve("stream.bin");

    assertOutput(
        "import std.filesystem.FileException import std.filesystem.FileReader import std.filesystem.FileSyncMode "
            + "import std.filesystem.FileWriter import std.filesystem.FileWriteMode "
            + "import std.filesystem.Path import std.filesystem.openRead import std.filesystem.openWrite "
            + "import std.io.Bytes import std.io.ReadChunk import std.io.bytes "
            + "import std.io.readAll import std.io.use import std.io.writeAll Void main() { "
            + "Path path = Path(value: \""
            + literal(file)
            + "\") FileWriter writer = openWrite(path: path, mode: FileWriteMode.CreateNew) "
            + "use<Integer>(resource: writer, body: () { "
            + "writeAll(writer: writer, content: bytes(values: [1, 2, 3])) "
            + "writer.flush() writer.sync(mode: FileSyncMode.DataAndMetadata) 0 }) "
            + "try { writer.write(content: bytes(values: [9])) } "
            + "catch FileException error { printLine(error.operation) printLine(error.reason) } "
            + "try { openWrite(path: path, mode: FileWriteMode.CreateNew) } "
            + "catch FileException error { printLine(error.operation) printLine(error.reason) } "
            + "FileReader reader = openRead(path: path) Bytes content = use<Bytes>("
            + "resource: reader, body: () { readAll(reader: reader, maximumBytes: 3) }) "
            + "printLine(content.at(index: 0)) printLine(content.at(index: 2)) "
            + "printLine(content == bytes(values: [1, 2, 3])) "
            + "reader.close() reader.close() try { reader.read(maximumBytes: 1) } "
            + "catch FileException error { "
            + "printLine(error.operation) printLine(error.reason) } }",
        "FileOperation.Write",
        "FileFailure.Closed",
        "FileOperation.OpenWrite",
        "FileFailure.AlreadyExists",
        "1",
        "3",
        "true",
        "FileOperation.Read",
        "FileFailure.Closed");
  }

  @Test
  void boundsTextReadsAndRejectsMalformedUtf8() throws Exception {
    Path large = directory.resolve("large.txt");
    Path malformed = directory.resolve("malformed.txt");
    Files.writeString(large, "four", StandardCharsets.UTF_8);
    Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});

    assertOutput(
        "import std.filesystem.Path import std.filesystem.readText "
            + "import std.io.StreamException import std.io.TextEncoding import std.io.TextException "
            + "Void main() { try { readText(path: Path(value: \""
            + literal(large)
            + "\"), encoding: TextEncoding.Utf8, maximumBytes: 3) } "
            + "catch StreamException error { printLine(error.reason) } "
            + "try { readText(path: Path(value: \""
            + literal(malformed)
            + "\"), encoding: TextEncoding.Utf8, maximumBytes: 2) } "
            + "catch TextException error { printLine(error.reason) } }",
        "StreamFailure.LimitExceeded",
        "TextFailure.InvalidInput");
  }

  @Test
  void closesOpenFileResourcesAtTheExecutionBoundary() {
    AtomicInteger closes = new AtomicInteger();
    FileSystem fileSystem =
        new FileSystem() {
          @Override
          public PlatformByteReader openRead(String path) {
            return new PlatformByteReader() {
              @Override
              public PlatformRead read(int maximumBytes) {
                return PlatformRead.Eof.INSTANCE;
              }

              @Override
              public void close() {
                closes.incrementAndGet();
              }
            };
          }

          @Override
          public PlatformByteWriter openWrite(String path, FileWriteMode mode) {
            throw new UnsupportedOperationException();
          }
        };
    assertOutput(
        platform(fileSystem),
        "import std.filesystem.FileReader import std.filesystem.Path "
            + "import std.filesystem.openRead Void main() { "
            + "FileReader reader = openRead(path: Path(value: \"resource.bin\")) "
            + "printLine(reader.read(maximumBytes: 1)) }",
        "ReadChunk.Eof");

    assertEquals(1, closes.get());
  }

  @Test
  void exposesFileCloseFailuresFromStructuredUse() {
    PlatformFileException closeFailure =
        new PlatformFileException(
            FileOperation.CLOSE,
            FileFailure.IO,
            "resource.bin",
            "close failed",
            new IllegalStateException("close"));
    FileSystem fileSystem =
        new FileSystem() {
          @Override
          public PlatformByteReader openRead(String path) {
            return new PlatformByteReader() {
              @Override
              public PlatformRead read(int maximumBytes) {
                return PlatformRead.Eof.INSTANCE;
              }

              @Override
              public void close() {
                throw closeFailure;
              }
            };
          }

          @Override
          public PlatformByteWriter openWrite(String path, FileWriteMode mode) {
            throw new UnsupportedOperationException();
          }
        };

    assertOutput(
        platform(fileSystem),
        "import std.filesystem.FileException import std.filesystem.FileReader "
            + "import std.filesystem.Path import std.filesystem.openRead import std.io.use "
            + "Void main() { FileReader reader = openRead(path: Path(value: \"resource.bin\")) "
            + "try { use<Integer>(resource: reader, body: () { 7 }) } "
            + "catch FileException error { printLine(error.operation) printLine(error.reason) } }",
        "FileOperation.Close",
        "FileFailure.Io");
  }

  @Test
  void hidesFileSystemIntrinsicsFromApplications() {
    assertFalse(compile("Void main() { __fileClose(resource: null) }").isSuccess());
  }

  private static String literal(Path path) {
    return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static SystemPlatform platform(FileSystem fileSystem) {
    return new SystemPlatform() {
      @Override
      public FileSystem fileSystem() {
        return fileSystem;
      }

      @Override
      public dev.w0fv1.norm.platform.time.SystemClock clock() {
        return JdkSystemPlatform.standard().clock();
      }

      @Override
      public dev.w0fv1.norm.platform.http.HttpTransport httpTransport() {
        return JdkSystemPlatform.standard().httpTransport();
      }
    };
  }
}
