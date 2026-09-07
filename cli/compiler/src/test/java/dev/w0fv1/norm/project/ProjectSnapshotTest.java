package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectSnapshotTest {
  @Test
  void bundlesCapturedResourcesWhenWorkingFilesChange(@TempDir Path root) throws Exception {
    Path module = Files.createDirectories(root.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Path entry = Files.writeString(module.resolve("main.norm"), "package sample Void main() {}");
    Path resources = Files.createDirectories(module.resolve("resources"));
    Path resource = Files.writeString(resources.resolve("value.txt"), "captured");
    try (var projects = ProjectEnvironment.bootstrap(new NormRuntime()).projectLoader()) {
      var snapshot = projects.load(entry);
      Files.writeString(resource, "changed");
      Files.writeString(resources.resolve("later.txt"), "new");
      Path output = root.resolve("bundle");
      Path bundled = new ApplicationBundleWriter().writeDirectory(snapshot, output);
      assertEquals(
          "captured", Files.readString(bundled.getParent().resolve("resources/value.txt")));
      assertFalse(Files.exists(bundled.getParent().resolve("resources/later.txt")));
    }
  }

  @Test
  void rejectsChangedDependencyArchive(@TempDir Path root) throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path dependency = Files.createDirectories(root.resolve("library/sample/lib"));
    Path module =
        Files.writeString(
            dependency.resolve("module.norm"),
            "Module module() { return module(name: \"sample.lib\", version: 1, exports: [\"Value\"]) }");
    Files.writeString(
        dependency.resolve("Value.norm"),
        "package sample.lib public Integer answer() { return 42 }");
    Path repository = root.resolve("repository");
    try (var projects = environment.projectLoader()) {
      new ModulePackager(projects).packageModule(module, repository);
    }
    Path application = Files.createDirectories(root.resolve("application/app"));
    Path entry = Files.writeString(application.resolve("Main.norm"), "package app Void main() {}");
    Files.writeString(
        application.resolve("module.norm"),
        "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"sample.lib\", version: 1)]) }");
    try (var projects = environment.projectLoader(repository, root.resolve("cache"))) {
      var snapshot = projects.load(entry);
      Files.writeString(repository.resolve("sample/lib/1/lib-1.nar"), "changed");
      assertThrows(
          java.io.IOException.class,
          () -> new ApplicationBundleWriter().writeDirectory(snapshot, root.resolve("bundle")));
    }
  }
}
