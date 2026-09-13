package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentApplicationResourcesTest {
  @TempDir Path directory;

  @Test
  void materializesCurrentResourcesWhenCompiledSourcesAreReused() throws Exception {
    Path root = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        root.resolve("module.norm"), "Module module() { module(name: \"sample\", version: 1) }");
    Path entry = root.resolve("main.norm");
    Files.writeString(entry, "package sample\nVoid main() {}");
    Path resource = root.resolve("resources/public/value.txt");
    Files.createDirectories(resource.getParent());
    Files.writeString(resource, "first");
    var timestamp = Files.getLastModifiedTime(resource);
    var environment = ProjectEnvironment.persistent(new NormRuntime());
    for (int attempt = 0; attempt < 3; attempt++) {
      if (attempt == 1) {
        Files.writeString(resource, "second");
        Files.setLastModifiedTime(resource, timestamp);
      } else if (attempt == 2) Files.delete(resource);
      var progress = new ArrayList<String>();
      try (var runner = ApplicationRunner.persistent(environment);
          var compilation = runner.compileApplication(entry, progress::add, List.of())) {
        assertTrue(compilation.result().isSuccess(), compilation.result().diagnostics().toString());
        if (attempt > 0) assertTrue(progress.contains("Reused compiled Norm sources"));
        Path copied =
            compilation
                .application()
                .orElseThrow()
                .annotations()
                .classes()
                .resolve("public/value.txt");
        if (attempt == 2) assertFalse(Files.exists(copied));
        else assertEquals(attempt == 0 ? "first" : "second", Files.readString(copied));
      }
    }
  }
}
