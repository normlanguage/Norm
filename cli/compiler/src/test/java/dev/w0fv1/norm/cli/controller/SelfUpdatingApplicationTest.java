package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(180)
final class SelfUpdatingApplicationTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runningNormAgentPublishesItsOwnCodeAndDependency(boolean addDependency) throws Exception {
    directory = Files.createDirectory(directory.resolve("agent"));
    Path entry = directory.resolve("application.norm");
    Path module = directory.resolve("module.norm");
    String moduleText = "Module module() { module(name: \"agent\", version: 1) }";
    Files.writeString(module, moduleText);
    String replacementModule = moduleText;
    if (addDependency) {
      Path javaSource = directory.resolve("AgentDependency.java");
      Files.writeString(
          javaSource,
          "public final class AgentDependency { public static int value() { return 42; } }");
      Path classes = Files.createDirectory(directory.resolve("java-classes"));
      assertEquals(
          0,
          ToolProvider.getSystemJavaCompiler()
              .run(null, null, null, "-d", classes.toString(), javaSource.toString()));
      Path jar = directory.resolve("dependency.jar");
      try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
        archive.putNextEntry(new JarEntry("AgentDependency.class"));
        archive.write(Files.readAllBytes(classes.resolve("AgentDependency.class")));
        archive.closeEntry();
      }
      replacementModule =
          """
          Module module() { module(name: "agent", version: 1,
            binding: jarBinding(target: localJar(path: "dependency.jar", integrity: sha256("%s")),
              api: [jarType(name: "AgentDependency", members: ["value"])])) }
          """
              .formatted(Sha256Digest.compute(jar).value());
    }
    Files.writeString(directory.resolve("next-module.txt"), replacementModule);
    String helper = "package agent\nInteger helperValue() { 42 }";
    Files.writeString(directory.resolve("next-helper.txt"), helper);
    String imports =
        """
        package agent
        import std.application.applicationDirectory
        import std.application.arguments
        import std.filesystem.Path
        import std.filesystem.readText
        import std.filesystem.writeTextAtomic
        import std.io.TextEncoding
        import std.concurrent.delay
        import std.time.duration
        """;
    String publish =
        """
          if (count == 1) {
            var root = applicationDirectory().value
            writeTextAtomic(path: Path(value: root + "/helper.norm"),
              text: readText(path: Path(value: root + "/next-helper.txt"), encoding: TextEncoding.Utf8, maximumBytes: 1048576),
              encoding: TextEncoding.Utf8)
            writeTextAtomic(path: Path(value: root + "/module.norm"),
              text: readText(path: Path(value: root + "/next-module.txt"), encoding: TextEncoding.Utf8, maximumBytes: 1048576),
              encoding: TextEncoding.Utf8)
            writeTextAtomic(path: Path(value: root + "/application.norm"),
              text: readText(path: Path(value: root + "/next-source.txt"), encoding: TextEncoding.Utf8, maximumBytes: 1048576),
              encoding: TextEncoding.Utf8)
            printLine("agent-saved")
          }
        """;
    String original =
        imports
            + """
        Void main() {
          printLine("boot")
          var count = 0
          for (arguments().isEmpty() && count < 300) {
        """
            + publish
            + """
            printLine("old:" + count.toString())
            count = count + 1
            delay(duration(seconds: 0, nanoseconds: 100000000))
          }
        }
        """;
    String updated =
        imports
            + "import agent.helperValue\n"
            + """
        interface AgentValue<T> { T get() }
        class AgentBox<T> implements AgentValue<T> {
          T value
          AgentBox(T value) { this.value = value }
          T get() { value }
        }
        interface AgentCounter { Integer current() }
        class AgentState implements AgentCounter {
          Integer count
          Integer current() { count }
        }
        Integer addedFunction() { %s }
        Void main() {
          printLine("boot")
          var count = 0
          for (arguments().isEmpty() && count < 300) {
        """
                .formatted(
                    addDependency ? "agentDependencyValue() + helperValue() - 42" : "helperValue()")
            + publish
            + """
            AgentCounter state = AgentState(count: count)
            AgentValue<Integer> box = AgentBox<Integer>(addedFunction())
            printLine("new:" + state.current().toString() + ":" + box.get().toString())
            if (count >= 4) { break }
            count = count + 1
            delay(duration(seconds: 0, nanoseconds: 100000000))
          }
        }
        """;
    Files.writeString(entry, original);
    Files.writeString(directory.resolve("next-source.txt"), updated);
    Path output = directory.resolve("stdout.txt");
    Path diagnostics = directory.resolve("stderr.txt");
    var command =
        java.util.List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--add-modules",
            "ALL-SYSTEM",
            "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
            "--sun-misc-unsafe-memory-access=allow",
            "--enable-native-access=org.graalvm.truffle",
            "-Dpolyglot.engine.WarnInterpreterOnly=false",
            "--module-path",
            System.getProperty("norm.test.compilerJar")
                + java.io.File.pathSeparator
                + Path.of(System.getProperty("norm.test.runtimeDirectory"), "lib"),
            "--module",
            "dev.w0fv1.norm/dev.w0fv1.norm.cli.Main",
            "run",
            "--debug",
            entry.toString());
    var warmCommand = new java.util.ArrayList<>(command);
    warmCommand.addAll(java.util.List.of("--", "seed"));
    var warm =
        new ProcessBuilder(warmCommand)
            .directory(directory.toFile())
            .redirectOutput(directory.resolve("warm-stdout.txt").toFile())
            .redirectError(directory.resolve("warm-stderr.txt").toFile())
            .start();
    try {
      assertTrue(warm.waitFor(60, TimeUnit.SECONDS), "prepared source startup timed out");
      assertEquals(0, warm.exitValue(), Files.readString(directory.resolve("warm-stderr.txt")));
      assertEquals(original, Files.readString(entry));
      assertEquals(moduleText, Files.readString(module));
      assertTrue(Files.notExists(directory.resolve("helper.norm")));
    } finally {
      if (warm.isAlive()) {
        warm.destroyForcibly();
        warm.waitFor(10, TimeUnit.SECONDS);
      }
    }
    var process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectError(diagnostics.toFile())
            .start();
    try {
      assertTrue(process.waitFor(90, TimeUnit.SECONDS), "self-updating agent timed out");
      String diagnosticText = Files.readString(diagnostics);
      assertEquals(0, process.exitValue(), diagnosticText);
      assertTrue(diagnosticText.contains("Reused prepared application"), diagnosticText);
      var lines = Files.readString(output).lines().toList();
      assertEquals(1, lines.stream().filter("boot"::equals).count(), lines.toString());
      assertEquals(1, lines.stream().filter("agent-saved"::equals).count(), lines.toString());
      assertTrue(
          lines.stream().anyMatch(line -> line.startsWith("new:") && line.endsWith(":42")),
          diagnosticText + lines);
      var counters =
          lines.stream()
              .filter(line -> line.startsWith("old:") || line.startsWith("new:"))
              .map(line -> Integer.parseInt(line.split(":")[1]))
              .toList();
      for (int index = 0; index < counters.size(); index++)
        assertEquals(index, counters.get(index), lines.toString());
      assertEquals(updated, Files.readString(entry));
      assertEquals(replacementModule, Files.readString(module));
      assertEquals(helper, Files.readString(directory.resolve("helper.norm")));
      System.out.printf("agent addDependency=%s stdout=%s%n", addDependency, lines);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }
}
