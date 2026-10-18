package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputChangeMonitorTest {
  @TempDir Path directory;

  @Test
  void observesAtomicFileReplacementAndMissingInputRecovery() throws Exception {
    Path entry = directory.resolve("entry.norm");
    Files.writeString(entry, "old");
    Path missing = directory.resolve("missing.norm");
    var inputs =
        new ProjectInputSnapshot(List.of(FileSnapshot.capture(entry)), List.of(), List.of(missing));
    try (var monitor = new InputChangeMonitor()) {
      monitor.update(inputs);
      Path replacement = directory.resolve("replacement");
      Files.writeString(replacement, "new");
      Files.move(replacement, entry, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      assertTrue(monitor.awaitChange(Duration.ofSeconds(5)));
      assertFalse(inputs.matches());
      monitor.update(
          new ProjectInputSnapshot(
              List.of(FileSnapshot.capture(entry)), List.of(), List.of(missing)));
      Files.writeString(missing, "recovered");
      assertTrue(monitor.awaitChange(Duration.ofSeconds(5)));
    }
  }

  @Test
  void observesSourcesCreatedInNestedDirectories() throws Exception {
    var inputs =
        new ProjectInputSnapshot(
            List.of(), List.of(DirectorySnapshot.capture(directory, true)), List.of());
    try (var monitor = new InputChangeMonitor()) {
      monitor.update(inputs);
      Path nested = Files.createDirectories(directory.resolve("nested"));
      assertTrue(monitor.awaitChange(Duration.ofSeconds(5)));
      monitor.update(inputs);
      Files.writeString(nested.resolve("new.norm"), "source");
      assertTrue(monitor.awaitChange(Duration.ofSeconds(5)));
      assertFalse(inputs.matches());
    }
  }
}
