package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void loadsEveryModuleSourceAndExportsOnlyDeclaredSources() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Path exported =
        source(
            root,
            "sample/library/Value.norm",
            "package sample.library public Integer value() { return 1 }");
    Path internal =
        source(
            root,
            "sample/internal/Hidden.norm",
            "package sample.internal public Integer hidden() { return 2 }");
    module(root, "sample", "library.Value");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(3, sourceSet.sources().size());
      assertEquals(Set.of(exported.toAbsolutePath().normalize()), sourceSet.exportedSourcePaths());
      assertEquals(
          Set.of(
              entry.toAbsolutePath().normalize(),
              exported.toAbsolutePath().normalize(),
              internal.toAbsolutePath().normalize(),
              root.resolve("sample/module.norm").toAbsolutePath().normalize()),
          sourceSet.inputPaths());
    }
  }

  @Test
  void rejectsSourcesWhosePackageDoesNotMatchTheirPath() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("package"));
    Path entry = source(root, "sample/Main.norm", "package sample.other Void main() {}");
    module(root, "sample");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("must declare package 'sample'"));
    }
  }

  @Test
  void excludesNestedModulesFromTheOuterSourceSet() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("nested"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    module(root, "sample");
    Path nested = root;
    module(nested, "vendor", "Value");
    Path nestedSource =
        source(nested, "vendor/Value.norm", "package vendor public Integer value() { return 1 }");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          List.of(SourceFile.read(entry).id()),
          sourceSet.sources().stream().map(SourceFile::id).toList());
      assertFalse(sourceSet.inputPaths().contains(nestedSource.toAbsolutePath().normalize()));
    }
  }

  @Test
  void includesPackageSourceNamedModuleNorm() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("same-name"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Path packageSource =
        source(
            root,
            "sample/internal/module.norm",
            "package sample.internal public Integer value() { return 1 }");
    module(root, "sample", "internal.module");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertTrue(sourceSet.inputPaths().contains(packageSource.toAbsolutePath().normalize()));
      assertEquals(2, sourceSet.sources().size());
    }
  }

  @Test
  void treatsAFileWithoutModuleConfigurationAsStandalone() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("standalone"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Path peer = source(root, "sample/Peer.norm", "package sample Integer value() { return 1 }");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(1, sourceSet.sources().size());
      assertEquals(Set.of(entry.toAbsolutePath().normalize()), sourceSet.inputPaths());
      assertFalse(sourceSet.inputPaths().contains(peer.toAbsolutePath().normalize()));
    }
  }

  @Test
  void rejectsConfigurationDirectoriesThatDoNotMatchTheModuleName() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("wrong-root"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: []) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("must match module name 'sample'"));
    }
  }

  @Test
  void keepsTheModuleDirectoryAsProjectRootWhenConfigurationIsInvalid() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("invalid-configuration"));
    Path entry =
        source(
            root,
            "sample/internal/Value.norm",
            "package sample.internal Integer value() { return 1 }");
    Path modulePath = root.resolve("sample/module.norm");
    Files.createDirectories(modulePath.getParent());
    Files.writeString(
        modulePath, "Module module() { return module(name: \"sample\", version: 0, exports: []) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      assertEquals(
          root.resolve("sample").toAbsolutePath().normalize(),
          projects.projectRoot(SourceFile.read(entry), List.of()));
    }
  }

  @Test
  void resolvesTransitiveDependenciesWhenLoadingADependencySourceDirectly() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("dependency-editor"));
    Path repository = Files.createDirectories(root.resolve("dependencies"));
    Path base = Files.createDirectories(repository.resolve("base"));
    Path entry = source(base, "Value.norm", "package base public Integer value() { return 1 }");
    Files.writeString(
        base.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"], dependencies: [dependency(name: \"util\", version: 1)]) }");
    Path util = Files.createDirectories(repository.resolve("util"));
    source(util, "Value.norm", "package util public Integer utility() { return 2 }");
    Files.writeString(
        util.resolve("module.norm"),
        "Module module() { return module(name: \"util\", version: 1, exports: [\"Value\"]) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          Set.of(
              base.resolve("module.norm").toAbsolutePath().normalize(),
              util.resolve("module.norm").toAbsolutePath().normalize()),
          sourceSet.modulePaths());
    }
  }

  @Test
  void rejectsPackagesOwnedByMoreThanOneModule() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("split-package"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    source(
        root,
        "sample/shared/RootValue.norm",
        "package sample.shared public Integer rootValue() { return 1 }");
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"Main\"], dependencies: [dependency(name: \"sample.shared\", version: 1)]) }");
    Path dependency = Files.createDirectories(root.resolve("dependencies/sample/shared"));
    source(
        root.resolve("dependencies"),
        "sample/shared/DependencyValue.norm",
        "package sample.shared public Integer dependencyValue() { return 2 }");
    Files.writeString(
        dependency.resolve("module.norm"),
        "Module module() { return module(name: \"sample.shared\", version: 1, exports: [\"DependencyValue\"]) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("package 'sample.shared'"));
      assertTrue(exception.getMessage().contains("sample@1"));
      assertTrue(exception.getMessage().contains("sample.shared@1"));
    }
  }

  @Test
  void rejectsModulesThatCollideWithPreludeIdentity() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("reserved-module"));
    Path entry = source(root, "std/Main.norm", "package std Void main() {}");
    module(root, "std", "Main");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("module name 'std' is reserved"));
    }
  }

  private static ProjectEnvironment environment() throws IOException {
    return ProjectEnvironment.bootstrap(new NormRuntime());
  }

  private static void module(Path root, String name, String... exports) throws IOException {
    String values =
        java.util.Arrays.stream(exports)
            .map(value -> "\"" + value + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
    Path modulePath =
        root.resolve(name.replace('.', java.io.File.separatorChar)).resolve("module.norm");
    Files.createDirectories(modulePath.getParent());
    Files.writeString(
        modulePath,
        "Module module() { return module(name: \""
            + name
            + "\", version: 1, exports: ["
            + values
            + "]) }");
  }

  private static Path source(Path root, String relativePath, String text) throws IOException {
    Path path = root.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text);
    return path;
  }
}
