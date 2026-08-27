package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            + "\"), encoding: TextEncoding.Utf8)) }",
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
            + "\"), encoding: TextEncoding.Utf8) } "
            + "catch FileException error { printLine(error.code) printLine(error.reason) } }",
        "NORM-FS-NOT-FOUND",
        "FileFailure.NotFound");
  }

  @Test
  void hidesFileSystemIntrinsicsFromApplications() {
    assertFalse(
        compile("Void main() { printLine(__fileReadText(path: \"missing\", encoding: \"UTF-8\")) }")
            .isSuccess());
  }

  private static String literal(Path path) {
    return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
