package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.store.DirectoryArtifactCache;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.runtime.PreparedApplication;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PreparedApplicationTest {
  @TempDir Path directory;

  @Test
  void reusesMaterializedResourcesAndRepairsChangedOrAddedContent() throws Exception {
    Path entry =
        Files.writeString(
            directory.resolve("main.norm"),
            "Void main() { printLine(\"reused\") } Module module() { module(dependencies: []) }");
    Files.writeString(
        Files.createDirectories(directory.resolve("resources")).resolve("value.txt"), "original");
    var store = new DirectoryArtifactCache(directory.resolve("store"), 4, 1024 * 1024);
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compilation = runner.compileApplication(entry)) {
      assertTrue(compilation.result().isSuccess());
      var content =
          new PreparedApplicationWriter().capture(compilation.application().orElseThrow());
      Path first;
      try (var lease = content.acquire(store)) {
        first = lease.path();
        try (var other = content.acquire(store)) {
          assertEquals(first, other.path());
          var text = new StringWriter();
          content.application().execute(other.path(), ExecutionContext.of(new PrintWriter(text)));
          assertEquals("reused" + System.lineSeparator(), text.toString());
        }
        Path resource = first.resolve("classes/value.txt");
        var stamp = Files.getLastModifiedTime(resource);
        Files.writeString(resource, "modified");
        Files.setLastModifiedTime(resource, stamp);
        try (var repaired = content.acquire(store)) {
          assertNotEquals(first, repaired.path());
          assertEquals("original", Files.readString(repaired.path().resolve("classes/value.txt")));
          Files.writeString(repaired.path().resolve("classes/extra.txt"), "extra");
          try (var clean = content.acquire(store)) {
            assertNotEquals(repaired.path(), clean.path());
            assertFalse(Files.exists(clean.path().resolve("classes/extra.txt")));
          }
        }
      }
    }
  }

  @Test
  void executesAfterCompilerClosesAndSourceDisappears() throws Exception {
    Path entry =
        Files.writeString(
            directory.resolve("main.norm"),
            """
            class Box {
              String value = "prepared"
              String read(String input = value) { input }
              String mutate(ref<String> input = &value) { *input = "updated"; value }
            }
            Void main() { var box = Box(); printLine(box.read()); printLine(box.mutate()) }
            """);
    Path output = directory.resolve("prepared");
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compilation = runner.compileApplication(entry)) {
      assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
      new PreparedApplicationWriter().write(compilation.application().orElseThrow(), output);
    }
    Files.delete(entry);
    for (int index = 0; index < 2; index++) {
      var text = new StringWriter();
      PreparedApplication.read(output).execute(output, ExecutionContext.of(new PrintWriter(text)));
      assertEquals(
          "prepared" + System.lineSeparator() + "updated" + System.lineSeparator(),
          text.toString());
    }
    try (var files = Files.walk(output)) {
      assertFalse(files.anyMatch(path -> path.toString().endsWith(".norm")));
    }
  }
}
