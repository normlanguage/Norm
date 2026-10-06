package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.InputWatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectInputSnapshotTest {
  @Test
  void derivesChangeMatchingAndWatchRulesFromTheCapturedInputs(@TempDir Path root)
      throws Exception {
    Path sourceRoot = Files.createDirectories(root.resolve("source"));
    Files.writeString(sourceRoot.resolve("Main.norm"), "Void main() {}");
    Path nested = Files.createDirectories(sourceRoot.resolve("nested"));
    Files.writeString(nested.resolve("Library.norm"), "public Integer value() { return 1 }");
    Path jar = Files.writeString(root.resolve("external.jar"), "binding");
    Path absent = root.resolve("dependency.nar");
    var inputs =
        new ProjectInputSnapshot(
            List.of(FileSnapshot.capture(jar)),
            List.of(DirectorySnapshot.capture(sourceRoot, true)),
            List.of(absent));
    assertTrue(inputs.matches());
    assertTrue(inputs.affects(jar));
    assertTrue(inputs.affects(absent));
    assertTrue(inputs.affects(sourceRoot.resolve("New.norm")));
    assertTrue(inputs.affects(sourceRoot));
    assertTrue(inputs.affects(nested));
    assertFalse(inputs.affects(sourceRoot.resolve("notes.txt")));
    assertFalse(inputs.affects(root.resolve("unrelated.jar")));
    assertTrue(inputs.watches().contains(new InputWatch(root, "external.jar")));
    assertTrue(inputs.watches().contains(new InputWatch(root, "dependency.nar")));
    assertTrue(inputs.watches().contains(new InputWatch(sourceRoot, "**/*.norm")));
    Files.writeString(jar, "updated binding");
    assertFalse(inputs.matches());
  }
}
