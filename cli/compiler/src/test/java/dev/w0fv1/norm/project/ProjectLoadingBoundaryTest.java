package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLoadingBoundaryTest {
  @TempDir Path root;

  @Test
  void keepsTraversalArchiveMaterializationAndBindingPreparationOutsideTheFacade() {
    Set<String> internals =
        Set.of(
            "resolveGraph",
            "resolveDependencies",
            "resolveArchivedDependency",
            "generateArchivedJarBinding");
    for (var method : ProjectLoader.class.getDeclaredMethods()) {
      assertFalse(internals.contains(method.getName()), method.toString());
    }
  }

  @Test
  void loadsASharedDependencyOnlyOnceAcrossADiamond() throws Exception {
    Path entry = module("app", "app", "left", "right");
    module("dependencies/left", "left", "shared");
    module("dependencies/right", "right", "shared");
    module("dependencies/shared", "shared");
    try (ProjectLoader loader = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      var project = loader.load(entry);
      assertEquals(4, project.sources().size());
      assertThrows(
          UnsupportedOperationException.class, () -> project.sources().add(SourceFile.read(entry)));
      assertEquals(
          1,
          project.sources().stream()
              .filter(source -> source.path().equals(root.resolve("dependencies/shared/Main.norm")))
              .count());
    }
  }

  @Test
  void reportsTheCompleteCycleWithoutRecursingIndefinitely() throws Exception {
    Path entry = module("app", "app", "left");
    module("dependencies/left", "left", "app");
    try (ProjectLoader loader = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      IOException failure = assertThrows(IOException.class, () -> loader.load(entry));
      assertTrue(failure.getMessage().contains("app@1 -> left@1 -> app@1"), failure.getMessage());
    }
  }

  @Test
  void usesCapturedEntryAndDependencyOverlaysInsteadOfDiskContents() throws Exception {
    Path entry = module("app", "app", "shared");
    Path shared = module("dependencies/shared", "shared");
    SourceFile capturedEntry = SourceFile.of(entry, "package app Void main() {}\n");
    SourceFile capturedDependency =
        SourceFile.of(shared, "package shared public Integer value() { 42 }\n");
    Files.writeString(entry, "invalid disk entry");
    Files.writeString(shared, "invalid disk dependency");
    try (ProjectLoader loader = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      var project = loader.load(capturedEntry, List.of(capturedDependency));
      assertEquals(capturedEntry.text(), project.primarySource().text());
      assertTrue(
          project.sources().stream()
              .anyMatch(source -> source.text().equals(capturedDependency.text())));
      assertFalse(
          project.sources().stream().anyMatch(source -> source.text().contains("invalid disk")));
    }
  }

  @Test
  void retriesAfterFailureWithoutRetainingPartialTraversalState() throws Exception {
    Path entry = module("app", "app", "shared");
    Path shared = module("dependencies/shared", "shared");
    Path manifest = shared.getParent().resolve("module.norm");
    String valid = Files.readString(manifest);
    Files.writeString(manifest, valid.replace("Main", "missing"));
    try (ProjectLoader loader = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      assertThrows(IOException.class, () -> loader.load(entry));
      Files.writeString(manifest, valid);
      assertEquals(
          Set.of(shared, entry),
          loader.load(entry).sources().stream()
              .map(SourceFile::path)
              .collect(java.util.stream.Collectors.toSet()));
    }
  }

  @Test
  void rejectsDifferentVersionsOfOneModuleInTheGraph() throws Exception {
    Path entry = module("shared", "shared", "child");
    Path manifest = entry.getParent().resolve("module.norm");
    Files.writeString(
        manifest, Files.readString(manifest).replace("version: 1, exports", "version: 2, exports"));
    module("dependencies/child", "child", "shared");
    module("dependencies/shared", "shared");
    try (ProjectLoader loader = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      IOException failure = assertThrows(IOException.class, () -> loader.load(entry));
      assertTrue(
          failure.getMessage().contains("module graph selects both shared@2 and shared@1"),
          failure.getMessage());
    }
  }

  private Path module(String directory, String name, String... dependencies) throws IOException {
    Path module = Files.createDirectories(root.resolve(directory));
    String requirements =
        java.util.Arrays.stream(dependencies)
            .map(
                dependency ->
                    "dependency(repository: \"github\", name: \"" + dependency + "\", version: 1)")
            .collect(java.util.stream.Collectors.joining(", "));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { module(name: \""
            + name
            + "\", version: 1, exports: [\"Main\"], dependencies: ["
            + requirements
            + "]) }");
    Path entry = module.resolve("Main.norm");
    Files.writeString(entry, "package " + name + " public Integer value() { 1 } Void main() {}\n");
    return entry;
  }
}
