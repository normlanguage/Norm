package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

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

final class PreparedApplicationCacheTest {
  @TempDir Path directory;

  @Test
  void validatesSmallInputIndexBeforeReadingPreparedContent() throws Exception {
    Path module = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"), "Module module() { module(name: \"sample\", version: 1) }");
    Path entry = Files.writeString(module.resolve("main.norm"), "package sample Void main() {} ");
    Path resources = Files.createDirectories(module.resolve("resources"));
    Files.write(resources.resolve("payload.bin"), new byte[4 * 1024 * 1024]);
    Path cacheRoot = directory.resolve("cache");
    var cache = new PreparedApplicationCache(cacheRoot);
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      assertTrue(
          runner
              .run(
                  entry,
                  ExecutionContext.of(new PrintWriter(new StringWriter())),
                  message -> {},
                  cache)
              .isSuccess());
    }
    Path payload;
    try (var files = Files.list(cacheRoot.resolve("content"))) {
      payload = files.filter(path -> path.toString().endsWith(".bin")).findFirst().orElseThrow();
    }
    try (var files = Files.list(cacheRoot)) {
      Path index = files.filter(path -> path.toString().endsWith(".bin")).findFirst().orElseThrow();
      assertTrue(Files.size(index) < Files.size(payload) / 4);
    }
    var key =
        new dev.w0fv1.norm.value.Sha256Digest(payload.getFileName().toString().replace(".bin", ""));
    new dev.w0fv1.norm.core.store.FileArtifactCache(
            cacheRoot.resolve("content"), 32, 512L * 1024 * 1024)
        .write(key, new byte[] {1, 2, 3});
    var timestamp = Files.getLastModifiedTime(entry);
    Files.writeString(entry, "package sample Void main() { printLine(1) }");
    Files.setLastModifiedTime(entry, timestamp);
    var changed = cache.read(entry);
    assertTrue(changed.content().isEmpty());
    assertEquals(1, changed.modules().size());
    Files.writeString(entry, "package sample Void main() {} ");
    assertThrows(java.io.IOException.class, () -> cache.read(entry));
    Files.delete(payload);
    var evicted = cache.read(entry);
    assertTrue(evicted.content().isEmpty());
    assertEquals(1, evicted.modules().size());
  }

  @Test
  void reusesPreparedApplicationAndInvalidatesSameTimestampChanges() throws Exception {
    Path entry =
        Files.writeString(
            directory.resolve("main.norm"),
            """
        Void main() { printLine("cached application") }
        Module module() { module(dependencies: []) }
        """);
    Path resource =
        Files.writeString(
            Files.createDirectories(directory.resolve("resources")).resolve("value.txt"), "first");
    var cache = new PreparedApplicationCache(directory.resolve("cache"));
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      assertTrue(
          runner
              .run(
                  entry,
                  ExecutionContext.of(new PrintWriter(new StringWriter())),
                  message -> {},
                  cache)
              .isSuccess());
    }
    var restored = new PreparedApplicationCache(directory.resolve("cache")).read(entry);
    assertTrue(restored.content().isPresent());
    assertEquals(1, restored.modules().size());
    Path staged = directory.resolve("staged");
    restored.content().orElseThrow().materialize(staged);
    var output = new StringWriter();
    PreparedApplication.read(staged).execute(staged, ExecutionContext.of(new PrintWriter(output)));
    assertEquals("cached application" + System.lineSeparator(), output.toString());
    assertEquals("first", Files.readString(staged.resolve("classes/value.txt")));
    var timestamp = Files.getLastModifiedTime(resource);
    Files.writeString(resource, "other");
    Files.setLastModifiedTime(resource, timestamp);
    var changedResource = cache.read(entry);
    assertTrue(changedResource.content().isEmpty());
    assertEquals(1, changedResource.modules().size());
    Files.writeString(resource, "first");
    assertTrue(cache.read(entry).content().isPresent());
    Files.delete(resource);
    assertTrue(cache.read(entry).content().isEmpty());
  }

  @Test
  void invalidatesAddedSourcesAndNewModuleConfigurations() throws Exception {
    Path module = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"), "Module module() { module(name: \"sample\", version: 1) }");
    Path entry = Files.writeString(module.resolve("main.norm"), "package sample Void main() {}");
    var cache = new PreparedApplicationCache(directory.resolve("cache"));
    try (var runner = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      assertTrue(
          runner
              .run(
                  entry,
                  ExecutionContext.of(new PrintWriter(new StringWriter())),
                  message -> {},
                  cache)
              .isSuccess());
    }
    assertTrue(cache.read(entry).content().isPresent());
    Path added = Files.writeString(module.resolve("added.norm"), "package sample Void added() {}");
    assertTrue(cache.read(entry).content().isEmpty());
    Files.delete(added);
    assertTrue(cache.read(entry).content().isPresent());
    Files.writeString(
        module.resolve("module.norm"), "Module module() { module(name: \"sample\", version: 2) }");
    assertTrue(cache.read(entry).content().isEmpty());
  }
}
