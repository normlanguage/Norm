package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.jar.JarOutputStream;
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
    assertTrue(noArguments.standardOut().contains("test"));
  }

  @Test
  void rejectsTestWithoutExactlyOneSourceFile() {
    Result result = run("test");

    assertEquals(ExitCode.USAGE_ERROR, result.exitCode());
    assertTrue(result.standardError().contains("'test' expects exactly one source file"));
    assertTrue(result.standardError().contains("Usage: norm test <file.norm>"));
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
  void runsASourceFileWithoutTheRunSubcommand() throws IOException {
    Path source = temporaryDirectory.resolve("hello.norm");
    Files.writeString(source, "Void main() { printLine(\"Hello from one command\") }");

    Result result = run(source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertEquals("Hello from one command" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void runsAnApplicationAndModuleDeclaredInOneSourceFile() throws IOException {
    Path source = temporaryDirectory.resolve("web.norm");
    Files.writeString(
        source,
        """
        package hello.web

        import std.application.Application

        Module module() {
          return module(name: "hello.web", version: 1)
        }

        class WebApplication implements Application {
          Void run() {
            printLine("single file application")
          }
        }

        public Application application() {
          return WebApplication()
        }
        """);

    Result result = run(source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertEquals("single file application" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void runsFunctionsAndValueConstructorsWithDefaults() throws IOException {
    Path source = temporaryDirectory.resolve("defaults.norm");
    Files.writeString(
        source,
        """
        value Server {
          String host = "0.0.0.0"
          Integer port = 8080
        }

        String address(String scheme = "http", String host = "localhost", Integer port = 80) {
          return scheme + "://" + host + ":" + port.toString()
        }

        Void main() {
          Server server = Server(host: "127.0.0.1")
          printLine(address(port: server.port))
          printLine(server.host + ":" + server.port.toString())
        }
        """);

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode());
    assertEquals(
        "http://localhost:8080"
            + System.lineSeparator()
            + "127.0.0.1:8080"
            + System.lineSeparator(),
        result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void prefersAnExactArityOverloadOverOneUsingDefaults() throws IOException {
    Path source = temporaryDirectory.resolve("default-overload.norm");
    Files.writeString(
        source,
        """
        String select(String value) { return "exact:" + value }
        String select(String value, String suffix = "default") { return value + suffix }
        Void main() { printLine(select("Norm")) }
        """);

    Result result = run("run", source.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertEquals("exact:Norm" + System.lineSeparator(), result.standardOut());
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
  void runsAnApplicationDeclarationFromItsModuleDirectory() throws IOException {
    Path application = Files.createDirectories(temporaryDirectory.resolve("application/sample"));
    Files.writeString(
        application.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Files.writeString(
        application.resolve("application.norm"),
        """
        package sample

        import std.application.Application

        class GreetingApplication implements Application {
          Void run() {
            printLine("Hello from application.norm")
          }
        }

        public Application application() {
          return GreetingApplication()
        }
        """);

    Result result = run("run", application.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertEquals("Hello from application.norm" + System.lineSeparator(), result.standardOut());
    assertEquals("", result.standardError());
  }

  @Test
  void resolvesAndPinsALocalJarInModuleNorm() throws IOException {
    Path module = Files.createDirectories(temporaryDirectory.resolve("resolve/sample"));
    Path jar = module.resolve("lib/sample.jar");
    Files.createDirectories(jar.getParent());
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.finish();
    }
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            binding: jarBinding(target: localJar(path: "lib/sample.jar"), api: [])
          )
        }
        """);

    Result result = run("resolve", module.toString());

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertTrue(result.standardOut().contains("Resolved sample@1 to sha256:"));
    String updated = Files.readString(modulePath);
    assertTrue(updated.contains("integrity: sha256(\""));
    assertFalse(Files.exists(module.resolve("lock.norm")));
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
  void exportsAFileOrderedApiTreeThatMirrorsTheModuleSources() throws IOException {
    Path sourceRoot = Files.createDirectories(temporaryDirectory.resolve("documentation-sources"));
    Path moduleRoot = Files.createDirectories(sourceRoot.resolve("sample"));
    Path library = Files.createDirectories(moduleRoot.resolve("library"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 7, exports: [\"library.sequences\"]) }");
    Files.writeString(
        library.resolve("sequences.norm"),
        "@Document(description: \"Sequence operations.\") package sample.library\n"
            + "import std.annotation.Document\n"
            + "@Document(description: \"Declared first.\") public Integer first("
            + "@Document(description: \"The input.\") Integer value) { return value }\n"
            + "@Document(description: \"Declared second.\") public Integer second() { return 2 }\n");
    Path output = temporaryDirectory.resolve("generated-api");

    Result result = run("docs", moduleRoot.toString(), "--output", output.toString(), "--strict");

    assertEquals(ExitCode.SUCCESS, result.exitCode(), result.standardError());
    assertTrue(Files.isRegularFile(output.resolve("module.api.json")));
    Path fileDocument = output.resolve("library/sequences.api.json");
    assertTrue(Files.isRegularFile(fileDocument));
    JsonObject module =
        JsonParser.parseString(Files.readString(output.resolve("module.api.json")))
            .getAsJsonObject();
    assertEquals("module", module.get("kind").getAsString());
    assertEquals("sample", module.getAsJsonObject("module").get("name").getAsString());
    assertEquals(
        "library/sequences.api.json",
        firstFile(module.getAsJsonArray("tree")).get("document").getAsString());
    JsonObject file = JsonParser.parseString(Files.readString(fileDocument)).getAsJsonObject();
    assertEquals("file", file.get("kind").getAsString());
    assertEquals(
        "library/sequences.norm", file.getAsJsonObject("source").get("path").getAsString());
    assertEquals(
        "Sequence operations.", file.getAsJsonObject("document").get("description").getAsString());
    JsonArray declarations = file.getAsJsonArray("declarations");
    assertEquals("first", declarations.get(0).getAsJsonObject().get("name").getAsString());
    assertEquals("second", declarations.get(1).getAsJsonObject().get("name").getAsString());
    assertEquals(
        "The input.",
        declarations
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parameters")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("document")
            .get("description")
            .getAsString());

    Path stale = output.resolve("library/removed.api.json");
    Files.writeString(stale, "{}");
    Result regenerated =
        run("docs", moduleRoot.toString(), "--output", output.toString(), "--strict");

    assertEquals(ExitCode.SUCCESS, regenerated.exitCode(), regenerated.standardError());
    assertFalse(Files.exists(stale));
  }

  @Test
  void requiresModuleNormAndRejectsUndocumentedExportedDeclarationsInStrictMode()
      throws IOException {
    Path missingModule = Files.createDirectories(temporaryDirectory.resolve("missing-module"));
    Result missing =
        run(
            "docs",
            missingModule.toString(),
            "--output",
            temporaryDirectory.resolve("missing-output").toString());
    assertEquals(ExitCode.INPUT_ERROR, missing.exitCode());
    assertTrue(missing.standardError().contains("module.norm"));

    Path sourceRoot = Files.createDirectories(temporaryDirectory.resolve("strict-sources"));
    Path moduleRoot = Files.createDirectories(sourceRoot.resolve("strict"));
    Path library = Files.createDirectories(moduleRoot.resolve("library"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        "Module module() { return module(name: \"strict\", version: 1, exports: [\"library.api\"]) }");
    Files.writeString(
        library.resolve("api.norm"),
        "package strict.library public Integer undocumented() { return 1 }");
    Path output = temporaryDirectory.resolve("strict-output");

    Result strictResult =
        run("docs", moduleRoot.toString(), "--output", output.toString(), "--strict");

    assertEquals(ExitCode.COMPILATION_ERROR, strictResult.exitCode());
    assertTrue(strictResult.standardError().contains("undocumented"));
    assertFalse(Files.exists(output));
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

  private static JsonObject firstFile(JsonArray entries) {
    for (var entry : entries) {
      JsonObject value = entry.getAsJsonObject();
      if (value.get("kind").getAsString().equals("file")) return value;
      if (value.get("kind").getAsString().equals("directory")) {
        JsonObject nested = firstFile(value.getAsJsonArray("children"));
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private record Result(int exitCode, String standardOut, String standardError) {}
}
