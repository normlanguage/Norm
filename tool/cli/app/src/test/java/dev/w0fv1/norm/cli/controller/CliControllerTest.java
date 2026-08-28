package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliControllerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void printsTheBuildVersion() {
    Result result = run("--version");

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertFalse(BuildMetadata.VERSION.contains("${"));
    assertEquals("norm " + BuildMetadata.VERSION + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void showsHelpForNoArgumentsAndAliases() {
    Result noArguments = run();
    Result alias = run("-h");

    assertEquals(ExitCode.SUCCESS, noArguments.exitCode());
    assertEquals(noArguments.standardOut(), alias.standardOut());
    assertTrue(noArguments.standardOut().contains("Usage: norm <command> [options]"));
    assertTrue(noArguments.standardOut().contains("version"));
  }

  @Test
  void reportsUnknownCommandsOnStandardError() {
    Result result = run("compile");

    assertEquals(ExitCode.USAGE_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-CLI-0001"));
    assertTrue(result.standardError().contains("unknown command 'compile'"));
  }

  @Test
  void rejectsUnexpectedCommandArguments() {
    Result result = run("version", "extra");

    assertEquals(ExitCode.USAGE_ERROR, result.exitCode());
    assertTrue(result.standardError().contains("NORM-CLI-0002"));
  }

  @Test
  void runsAHelloWorldSourceFile() throws IOException {
    Path source = temporaryDirectory.resolve("hello.norm");
    Files.writeString(source, "Void main() { printLine(\"Hello from Norm\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("Hello from Norm" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void loadsAndRunsTheEntrySourceRoot() throws IOException {
    Path app = Files.createDirectories(temporaryDirectory.resolve("src/sample"));
    Path math = Files.createDirectories(temporaryDirectory.resolve("src/sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry, "package sample import sample.math.twice Void main() { printLine(twice(7)) }");
    Files.writeString(
        math.resolve("Numbers.norm"),
        "package sample.math public Integer twice(Integer value) { return value * 2 }");
    Files.writeString(
        temporaryDirectory.resolve("src/sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"math.Numbers\"]) }");

    Result result = run("run", entry.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("14" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void runsPackagedStandardLibrarySources() throws IOException {
    Path source = temporaryDirectory.resolve("math.norm");
    Files.writeString(
        source,
        "import std.math.clamp Void main() { "
            + "printLine(clamp(value: 12, minimum: 0, maximum: 9)) }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals("9" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void rendersCompilerDiagnosticsForInvalidPrograms() throws IOException {
    Path source = temporaryDirectory.resolve("invalid.norm");
    Files.writeString(source, "Void main() { missing(\"value\") }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-NAME-0003"));
    assertTrue(result.standardError().contains("cannot find function or type 'missing'"));
  }

  @Test
  void rendersGuestRuntimeErrors() throws IOException {
    Path source = temporaryDirectory.resolve("runtime-error.norm");
    Files.writeString(source, "Void main() {\n  printLine(1 / 0)\n}");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.RUNTIME_ERROR, result.exitCode());
    assertEquals("", result.standardOut());
    assertTrue(result.standardError().contains("NORM-RUNTIME-0004"));
    assertTrue(result.standardError().contains(source.toUri().toString()));
    assertTrue(result.standardError().contains(":2:"));
  }

  @Test
  void writesAndReadsAFileThroughTheCliPlatform() throws IOException {
    Path source = temporaryDirectory.resolve("file-stream.norm");
    Path target = temporaryDirectory.resolve("message.txt");
    String path =
        target.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
    Files.writeString(
        source,
        "import std.filesystem.FileWriteMode import std.filesystem.FileWriter import std.filesystem.Path "
            + "import std.filesystem.openWrite import std.filesystem.readText "
            + "import std.io.TextEncoding import std.io.encodeText "
            + "import std.io.use import std.io.writeAll Void main() { "
            + "Path path = Path(value: \""
            + path
            + "\") FileWriter writer = openWrite(path: path, mode: FileWriteMode.CreateNew) "
            + "use<Integer>(resource: writer, body: () { "
            + "writeAll(writer: writer, content: encodeText(text: \"Norm CLI 文件\", "
            + "encoding: TextEncoding.Utf8)) 0 }) "
            + "printLine(readText(path: path, encoding: TextEncoding.Utf8, maximumBytes: 64)) }");

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertEquals("Norm CLI 文件" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void sendsAnHttpRequestThroughTheCliPlatform() throws Exception {
    Path source = temporaryDirectory.resolve("http.norm");
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> exchange =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  BufferedReader input =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.US_ASCII));
                  for (String line = input.readLine(); line != null && !line.isEmpty(); ) {
                    line = input.readLine();
                  }
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 200 OK\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNorm HTTP")
                              .getBytes(StandardCharsets.US_ASCII));
                } catch (IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              });
      Files.writeString(
          source,
          "import std.http.HttpRequest import std.http.HttpResponse import std.http.Uri "
              + "import std.http.get import std.http.systemHttpClient import std.io.TextEncoding "
              + "import std.io.decodeText import std.io.readAll import std.io.use "
              + "import std.time.Duration import std.time.duration Void main() { "
              + "HttpRequest request = get(uri: Uri(value: \"http://127.0.0.1:"
              + server.getLocalPort()
              + "/message\")) HttpResponse response = systemHttpClient().send(request: request, "
              + "timeout: duration(seconds: 5, nanoseconds: 0)) "
              + "printLine(use<String>(resource: response, body: () { decodeText(content: "
              + "readAll(reader: response, maximumBytes: 9), encoding: TextEncoding.Utf8) })) }");

      Result result = run("run", source.toString());

      assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
      assertEquals("Norm HTTP" + System.lineSeparator(), result.standardOut());
      assertEquals("", result.standardError());
      exchange.get();
    }
  }

  private static Result run(String... arguments) {
    var standardOut = new StringWriter();
    var standardError = new StringWriter();
    int exitCode =
        new CliController()
            .run(arguments, new PrintWriter(standardOut), new PrintWriter(standardError));
    return new Result(exitCode, standardOut.toString(), standardError.toString());
  }

  private record Result(int exitCode, String standardOut, String standardError) {}
}
