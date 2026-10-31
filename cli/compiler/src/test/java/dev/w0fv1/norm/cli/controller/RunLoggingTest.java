package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(180)
final class RunLoggingTest {
  @TempDir Path directory;
  private Path app;

  @BeforeEach
  void applicationWithNormAndJavaDependencies() throws Exception {
    app = Files.createDirectories(directory.resolve("shop"));
    var library = Files.createDirectories(directory.resolve("dependencies/demo/library"));
    Files.writeString(
        library.resolve("module.norm"),
        """
        Module module() { module(name: "demo.library", version: 1, exports: ["events"]) }
        """);
    Files.writeString(
        library.resolve("events.norm"),
        """
        package demo.library
        import std.logging.logDebug
        public Void emitLibrary() { logDebug("package-debug") }
        """);
    var source = directory.resolve("ExternalLogs.java");
    Files.writeString(
        source,
        """
        public final class ExternalLogs {
          public static void emit() {
            org.slf4j.LoggerFactory.getLogger("external.provider").debug("java-debug");
          }
        }
        """);
    var classes = Files.createDirectory(directory.resolve("classes"));
    var slf4j =
        Path.of(org.slf4j.Logger.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-classpath",
                slf4j.toString(),
                "-d",
                classes.toString(),
                source.toString()));
    var jar = app.resolve("dependency.jar");
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      archive.putNextEntry(new JarEntry("ExternalLogs.class"));
      archive.write(Files.readAllBytes(classes.resolve("ExternalLogs.class")));
      archive.closeEntry();
    }
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "shop", version: 1,
          dependencies: [dependency(repository: "github", name: "demo.library", version: 1)],
          binding: jarBinding(target: localJar(path: "dependency.jar", integrity: sha256("%s")),
            api: [jarType(name: "ExternalLogs", members: ["emit"])])) }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Files.writeString(
        app.resolve("main.norm"),
        """
        package shop
        import std.application.arguments
        import std.logging.logDebug
        import demo.library.emitLibrary
        Void main() {
          logDebug("application-debug")
          emitLibrary()
          externalLogsEmit()
          if !arguments().isEmpty() { printLine(arguments()[0]) }
        }
        """);
  }

  @Test
  void explicitDebugOverridesEverySeverityThresholdForOneInvocation() throws Exception {
    String outputs = "[{\"name\":\"std.logging.ConsoleLogOutputer\",\"level\":\"off\"}]";
    Result debug = run(outputs, "--debug", "--", "payload");
    assertEquals(0, debug.exitCode(), debug.err());
    assertEquals("payload" + System.lineSeparator(), debug.out());
    assertTrue(debug.err().contains("Checking prepared application"), debug.err());
    String appLine =
        debug
            .err()
            .lines()
            .filter(line -> line.endsWith(" [DEBUG] shop application-debug"))
            .findFirst()
            .orElseThrow();
    assertDoesNotThrow(() -> java.time.Instant.parse(appLine.substring(0, appLine.indexOf(' '))));
    assertTrue(debug.err().contains("[DEBUG] demo.library package-debug"), debug.err());
    assertTrue(debug.err().contains("[DEBUG] external.provider java-debug"), debug.err());
    Result normal = run(outputs, "--", "payload");
    assertEquals(0, normal.exitCode(), normal.err());
    assertEquals("payload" + System.lineSeparator(), normal.out());
    assertFalse(normal.err().contains("application-debug"), normal.err());
    assertFalse(normal.err().contains("package-debug"), normal.err());
    assertFalse(normal.err().contains("java-debug"), normal.err());
    assertFalse(normal.err().contains("Checking prepared application"), normal.err());
  }

  @Test
  void debugRetainsTheConfiguredSourceRoute() throws Exception {
    Result result =
        run(
            "[{\"name\":\"std.logging.ConsoleLogOutputer\",\"level\":\"off\",\"source\":\"demo.library\"}]",
            "--debug");
    assertEquals(0, result.exitCode(), result.err());
    assertTrue(result.err().contains("[DEBUG] demo.library package-debug"), result.err());
    assertFalse(result.err().contains("application-debug"), result.err());
    assertFalse(result.err().contains("java-debug"), result.err());
  }

  @Test
  void debugRetainsTheConfiguredFileDestination() throws Exception {
    var logs = directory.resolve("logs");
    var destination =
        java.util.Map.of(
            "name",
            "std.logging.FileLogOutputer",
            "level",
            "off",
            "options",
            java.util.Map.of("directory", logs.toString()));
    Result result = run(new com.google.gson.Gson().toJson(List.of(destination)), "--debug");
    assertEquals(0, result.exitCode(), result.err());
    assertFalse(result.err().contains("application-debug"), result.err());
    assertFalse(result.err().contains("package-debug"), result.err());
    assertFalse(result.err().contains("java-debug"), result.err());
    try (var paths = Files.walk(logs)) {
      var messages = new ArrayList<String>();
      for (var file : paths.filter(path -> path.toString().endsWith(".jsonl")).toList()) {
        for (var line : Files.readAllLines(file)) {
          var event = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
          assertEquals("DEBUG", event.get("level").getAsString());
          messages.add(event.get("message").getAsString());
        }
      }
      assertEquals(List.of("application-debug", "package-debug", "java-debug"), messages);
    }
  }

  @Test
  void debugAfterTheSeparatorIsAnApplicationArgument() throws Exception {
    Result result =
        run("[{\"name\":\"std.logging.ConsoleLogOutputer\",\"level\":\"off\"}]", "--", "--debug");
    assertEquals(0, result.exitCode(), result.err());
    assertEquals("--debug" + System.lineSeparator(), result.out());
    assertFalse(result.err().contains("application-debug"), result.err());
    assertFalse(result.err().contains("package-debug"), result.err());
    assertFalse(result.err().contains("java-debug"), result.err());
    assertFalse(result.err().contains("Checking prepared application"), result.err());
  }

  private Result run(String outputs, String... options) throws Exception {
    var arguments =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "--sun-misc-unsafe-memory-access=allow",
                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                "-cp",
                System.getProperty("norm.test.compilerJar")
                    + File.pathSeparator
                    + Path.of(System.getProperty("norm.test.runtimeDirectory"), "lib")
                    + File.separator
                    + "*",
                "dev.w0fv1.norm.cli.Main",
                "run",
                app.toString()));
    arguments.addAll(List.of(options));
    var out = Files.createTempFile(directory, "cli-", ".out");
    var err = Files.createTempFile(directory, "cli-", ".err");
    var builder =
        new ProcessBuilder(arguments).redirectOutput(out.toFile()).redirectError(err.toFile());
    builder.environment().put("NORM_LOG_LEVEL", "off");
    builder.environment().put("NORM_LOG_OUTPUTS", outputs);
    var process = builder.start();
    try {
      assertTrue(process.waitFor(90, TimeUnit.SECONDS), "CLI invocation timed out");
      return new Result(process.exitValue(), Files.readString(out), Files.readString(err));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  private record Result(int exitCode, String out, String err) {}
}
