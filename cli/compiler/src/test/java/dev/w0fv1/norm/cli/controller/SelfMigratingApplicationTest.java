package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(180)
final class SelfMigratingApplicationTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runningAgentRecoversAndMigratesItsOwnRetainedState(boolean warmCache) throws Exception {
    directory = Files.createDirectory(directory.resolve("agent"));
    Path entry = directory.resolve("application.norm");
    Files.writeString(
        directory.resolve("module.norm"),
        "Module module() { module(name: \"agent\", version: 1) }");
    String original = source(0, false);
    Files.writeString(entry, original);
    Files.writeString(directory.resolve("compile-error.txt"), original + "\nVoid invalid( {");
    for (int version = 1; version <= 3; version++) {
      Files.writeString(directory.resolve("attempt-" + version + ".txt"), "waiting");
      Files.writeString(directory.resolve("failed-" + version + ".txt"), source(version, true));
      Files.writeString(directory.resolve("next-" + version + ".txt"), source(version, false));
    }
    var command =
        new ArrayList<>(
            List.of(
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
                entry.toString()));
    if (warmCache) {
      var seed = new ArrayList<>(command);
      seed.addAll(List.of("--", "seed"));
      Path seedDiagnostics = directory.resolve("seed-stderr.txt");
      var process =
          new ProcessBuilder(seed)
              .directory(directory.toFile())
              .redirectOutput(directory.resolve("seed-stdout.txt").toFile())
              .redirectError(seedDiagnostics.toFile())
              .start();
      try {
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "prepared source startup timed out");
        assertEquals(0, process.exitValue(), Files.readString(seedDiagnostics));
        assertEquals(original, Files.readString(entry));
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
          process.waitFor(10, TimeUnit.SECONDS);
        }
      }
    }
    Path output = directory.resolve("stdout.txt");
    Path errors = directory.resolve("stderr.txt");
    var process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectError(errors.toFile())
            .start();
    try {
      assertTrue(process.waitFor(100, TimeUnit.SECONDS), "self-migrating agent timed out");
      String diagnostics = Files.readString(errors);
      assertEquals(0, process.exitValue(), diagnostics);
      List<String> lines = Files.readString(output).lines().toList();
      assertEquals(1, lines.stream().filter("boot"::equals).count(), lines.toString());
      if (warmCache) assertTrue(diagnostics.contains("Reused prepared application"), diagnostics);
      for (int version = 0; version <= 3; version++) {
        int expected = version;
        assertTrue(
            lines.stream().anyMatch(line -> line.startsWith("v" + expected + "|")),
            diagnostics + lines);
      }
      assertTrue(lines.contains("saved:compile-error.txt"), lines.toString());
      for (int version = 1; version <= 3; version++) {
        assertTrue(lines.contains("saved:failed-" + version + ".txt"), lines.toString());
        assertTrue(lines.contains("saved:next-" + version + ".txt"), lines.toString());
        assertTrue(lines.contains("attempted:" + version), diagnostics + lines);
        assertTrue(diagnostics.contains("intentional-migration-failure-" + version), diagnostics);
        assertEquals(
            "attempted", Files.readString(directory.resolve("attempt-" + version + ".txt")));
      }
      assertTrue(diagnostics.contains("intentional-migration-failure"), diagnostics);
      assertTrue(diagnostics.contains("error["), diagnostics);
      var states = lines.stream().filter(line -> line.matches("v[0-3]\\|.*")).toList();
      for (int index = 0; index < states.size(); index++) {
        String[] state = states.get(index).split("\\|");
        int version = Integer.parseInt(state[0].substring(1));
        assertEquals(index, Integer.parseInt(state[1]), lines.toString());
        assertEquals(100 + index, Integer.parseInt(state[2]), lines.toString());
        assertEquals(
            version < 2 ? Integer.toString(100 + index) : "n:" + (100 + index),
            state[3],
            lines.toString());
        assertEquals("1", state[4], lines.toString());
      }
      assertTrue(lines.contains("finished"), diagnostics + lines);
      assertEquals(source(3, false), Files.readString(entry));
      System.out.printf("self-migration warmCache=%s stdout=%s%n", warmCache, lines);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }

  private static String source(int version, boolean fail) {
    String field =
        version == 0
            ? "Integer count = 100"
            : version == 1 ? "Integer total = 100" : "String total = \"n:100\"";
    String migrate =
        switch (version) {
          case 0 -> "";
          case 1 ->
              """
          if (state.contains("count")) {
            state.transfer(source: "count", target: "total")
            state.remove("count")
          }
          """;
          case 2 ->
              """
          if (state.contains("total")) {
            state.write(field: State.total.field, value: "n:" + state.read<Integer>("total").toString())
          }
          """;
          case 3 -> "if (state.contains(\"legacy\")) { state.remove(\"legacy\") }";
          default -> throw new IllegalArgumentException("Unknown source version");
        };
    if (fail)
      migrate +=
          """
        markFailure(%d)
        state.write(field: State.ticks.field, value: -999)
        var audit = state.read<List<Integer>>("audits")
        audit.add(999)
        state.write(field: State.audits.field, value: audit)
        throw Exception(message: "intentional-migration-failure-%d")
        """
              .formatted(version, version);
    String value =
        version == 0
            ? "state.count.toString()"
            : version == 1 ? "state.total.toString()" : "state.total";
    String advance =
        version == 0
            ? "state.count = state.ticks"
            : version == 1
                ? "state.total = state.ticks"
                : "state.total = \"n:\" + state.ticks.toString()";
    return """
        package agent
        import std.application.applicationDirectory
        import std.application.arguments
        import std.filesystem.Path
        import std.filesystem.readText
        import std.filesystem.writeTextAtomic
        import std.io.TextEncoding
        import std.concurrent.delay
        import std.time.duration
        import std.core.Exception
        import std.runtime.Runtime
        import std.runtime.UpdateOutcome
        import std.runtime.StateMigration
        import std.runtime.MigrationState
        @StateMigration(convert: migrateState.function)
        class State {
          %s
          Integer ticks = 100
          List<Integer> audits = [1]
          %s
        }
        Void migrateState(MigrationState state) { %s }
        Void markFailure(Integer version) {
          var root = applicationDirectory().value
          writeTextAtomic(path: Path(value: root + "/attempt-" + version.toString() + ".txt"), text: "attempted", encoding: TextEncoding.Utf8)
          printLine("attempted:" + version.toString())
        }
        Boolean compileErrorObserved() {
          var latest = Runtime().latestUpdate()
          latest != null && latest.outcome == UpdateOutcome.Rejected
        }
        Boolean failureObserved(Integer version) {
          var root = applicationDirectory().value
          readText(path: Path(value: root + "/attempt-" + version.toString() + ".txt"), encoding: TextEncoding.Utf8, maximumBytes: 1024) == "attempted"
        }
        Void save(String file) {
          var root = applicationDirectory().value
          writeTextAtomic(path: Path(value: root + "/application.norm"),
            text: readText(path: Path(value: root + "/" + file), encoding: TextEncoding.Utf8, maximumBytes: 1048576),
            encoding: TextEncoding.Utf8)
          printLine("saved:" + file)
        }
        Void main() {
          printLine("boot")
          var state = State()
          var alias = state
          List<State> retained = [state, state]
          var phase = 0
          var savedAt = 0
          var tick = 0
          for (arguments().isEmpty() && tick < 400) {
            var version = %d
            if (phase == 0 && tick >= 2) {
              save("compile-error.txt")
              phase = 1
              savedAt = tick
            } else if (phase == 1 && tick - savedAt >= 3 && compileErrorObserved()) {
              save("failed-1.txt")
              phase = 2
              savedAt = tick
            } else if (phase == 2 && tick - savedAt >= 3 && failureObserved(1)) {
              save("next-1.txt")
              phase = 3
              savedAt = tick
            } else if (phase == 3 && version == 1 && tick - savedAt >= 3) {
              save("failed-2.txt")
              phase = 4
              savedAt = tick
            } else if (phase == 4 && version == 1 && tick - savedAt >= 3 && failureObserved(2)) {
              save("next-2.txt")
              phase = 5
              savedAt = tick
            } else if (phase == 5 && version == 2 && tick - savedAt >= 3) {
              save("failed-3.txt")
              phase = 6
              savedAt = tick
            } else if (phase == 6 && version == 2 && tick - savedAt >= 3 && failureObserved(3)) {
              save("next-3.txt")
              phase = 7
              savedAt = tick
            }
            require(condition: alias == state, message: "alias identity changed")
            require(condition: retained[0] == state && retained[1] == state, message: "retained graph identity changed")
            require(condition: state.audits.size() == 1 && state.audits[0] == 1, message: "failed migration mutated retained state")
            var hasLegacy = false
            for Field<State, ?> field : State.class.fields() {
              if (field.name() == "legacy") { hasLegacy = true }
            }
            require(condition: hasLegacy == (version < 3), message: "field deletion reflection does not match revision")
            printLine("v" + version.toString() + "|" + tick.toString() + "|" + state.ticks.toString() + "|" + %s + "|" + state.audits.size().toString())
            state.ticks = state.ticks + 1
            %s
            tick = tick + 1
            if (phase == 7 && version == 3 && tick - savedAt >= 3) { printLine("finished"); break }
            delay(duration(seconds: 0, nanoseconds: 100000000))
          }
        }
        """
        .formatted(
            field, version < 3 ? "String legacy = \"kept\"" : "", migrate, version, value, advance);
  }
}
