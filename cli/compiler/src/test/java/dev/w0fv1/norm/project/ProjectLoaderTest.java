package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLoaderTest {
  private static final String SHA256 = "0123456789abcdef".repeat(4);

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
  void usesOneStableAnalysisRequestForEverySourceInAModule() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("analysis-request"));
    Path first = source(root, "sample/First.norm", "package sample Integer first() { return 1 }");
    Path second =
        source(root, "sample/Second.norm", "package sample Integer second() { return 2 }");
    module(root, "sample", "First", "Second");

    try (ProjectLoader projects = environment().projectLoader()) {
      var firstRequest = projects.load(first).analysisCompilationRequest();
      var secondRequest = projects.load(second).analysisCompilationRequest();

      assertEquals(firstRequest, secondRequest);
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
  void derivesTheIdentityOfAnEmbeddedLocalModule() throws Exception {
    Path entry =
        source(
            temporaryDirectory,
            "web.norm",
            "package hello.web Module module() { return module(dependencies: []) } Void main() {}");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          new dev.w0fv1.norm.value.ModuleCoordinate("hello.web", 0),
          sourceSet.scope().coordinate(sourceSet.primarySource().id()).module());
    }
  }

  @Test
  void loadsAPackageLessEmbeddedApplicationAsALocalModule() throws Exception {
    Path entry =
        source(
            temporaryDirectory,
            "web.norm",
            "Module module() { return module(dependencies: []) } Void main() {}");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          dev.w0fv1.norm.value.ModuleCoordinate.localApplication(),
          sourceSet.scope().coordinate(sourceSet.primarySource().id()).module());
      assertEquals("", SourceHeader.parse(sourceSet.primarySource()).packageName().orElse(""));
    }
  }

  @Test
  void infersADirectoryModuleNameWithoutReadingNestedModules() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("inferred"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"), "Module module() { return module(dependencies: []) }");
    source(
        root,
        "sample/vendor/Value.norm",
        "package sample.vendor public Integer value() { return 1 }");
    Files.writeString(
        root.resolve("sample/vendor/module.norm"),
        "Module module() { return module(name: \"sample.vendor\", version: 1) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          new dev.w0fv1.norm.value.ModuleCoordinate("sample", 0),
          sourceSet.scope().coordinate(sourceSet.primarySource().id()).module());
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
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"], dependencies: [dependency(repository: \"github\", name: \"util\", version: 1)]) }");
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
  void retainsResolvedDependencyArchivesForApplicationPackaging() throws Exception {
    Path dependencyRoot = Files.createDirectories(temporaryDirectory.resolve("library/sample/lib"));
    Path dependencyModule = dependencyRoot.resolve("module.norm");
    Files.writeString(
        dependencyModule,
        "Module module() { return module(name: \"sample.lib\", version: 1, exports: [\"Value\"]) }");
    source(
        dependencyRoot, "Value.norm", "package sample.lib public Integer answer() { return 42 }");
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = environment();
    try (ProjectLoader projects = environment.projectLoader()) {
      new ModulePackager(projects).packageModule(dependencyModule, repository);
    }

    Path applicationRoot = Files.createDirectories(temporaryDirectory.resolve("application/app"));
    Path entry =
        source(
            applicationRoot,
            "Main.norm",
            "package app import sample.lib.answer Void main() { printLine(answer()) }");
    Files.writeString(
        applicationRoot.resolve("module.norm"),
        "Module module() { return module(name: \"app\", version: 1, dependencies: [dependency(repository: \"github\", name: \"sample.lib\", version: 1)]) }");

    try (ProjectLoader projects =
        environment.projectLoader(repository, temporaryDirectory.resolve("cache"))) {
      ProjectSourceSet sourceSet = projects.load(entry);

      assertEquals(
          repository.resolve("sample/lib/1/lib-1.nar").toAbsolutePath().normalize(),
          sourceSet
              .moduleArchives()
              .get(new dev.w0fv1.norm.value.ModuleCoordinate("sample.lib", 1)));
    }
  }

  @Test
  void rejectsOneModuleCoordinateSelectedFromDifferentRepositories() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("repository-conflict"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, dependencies: [dependency(repository: \"github\", name: \"left\", version: 1), dependency(repository: \"github\", name: \"right\", version: 1)]) }");
    Path left = Files.createDirectories(root.resolve("dependencies/left"));
    Files.writeString(
        left.resolve("module.norm"),
        "Module module() { return module(name: \"left\", version: 1, dependencies: [dependency(repository: \"github\", name: \"base\", version: 1)]) }");
    Path right = Files.createDirectories(root.resolve("dependencies/right"));
    Files.writeString(
        right.resolve("module.norm"),
        "Module module() { return module(name: \"right\", version: 1, dependencies: [dependency(repository: \"mirror\", name: \"base\", version: 1)]) }");
    Path base = Files.createDirectories(root.resolve("dependencies/base"));
    Files.writeString(
        base.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1) }");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("base@1"));
      assertTrue(exception.getMessage().contains("github"));
      assertTrue(exception.getMessage().contains("mirror"));
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
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"Main\"], dependencies: [dependency(repository: \"github\", name: \"sample.shared\", version: 1)]) }");
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

  @Test
  void reservesInternalModuleNames() throws Exception {
    Path entry =
        source(
            temporaryDirectory,
            "web.norm",
            "Module module() { return module(name: \"__private\", version: 1) } Void main() {}");

    try (ProjectLoader projects = environment().projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(
          exception.getMessage().contains("module name '__private' is reserved"),
          exception::getMessage);
    }
  }

  @Test
  void evaluatesOneMavenJarBindingFromModuleConfiguration() throws Exception {
    Path modulePath = temporaryDirectory.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        JarBinding libraryBinding() {
          List<JarType> api = []
          api.add(
            jarType(
              name: "StringUtils",
              members: ["isBlank"],
              overloads: [
                jarOverload(name: "reverse", parameterTypes: ["java.lang.String"])
              ]
            )
          )
          JarTarget target = mavenJar(
            group: "org.apache.commons",
            artifact: "commons-lang3",
            version: "3.20.0",
            resolution: sha256("%s")
          )
          return jarBinding(target: target, api: api)
        }

        Module module() {
          String namespace = "commons"
          String artifact = "lang"
          return module(
            name: namespace + "." + artifact,
            version: 1,
            binding: libraryBinding()
          )
        }
        """
            .formatted(SHA256));

    try (ProjectLoader projects = environment().projectLoader()) {
      var descriptor = projects.evaluateModule(SourceFile.read(modulePath));
      MavenJarTarget target = (MavenJarTarget) descriptor.binding().orElseThrow().target();

      assertEquals(
          new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
          target.coordinate());
      assertEquals(Sha256Digest.parse(SHA256), target.resolution().orElseThrow());
      assertEquals(List.of("StringUtils"), descriptor.exports());
      assertEquals(
          List.of(
              new JarBindingType(
                  "StringUtils",
                  List.of("isBlank"),
                  List.of(new JarBindingOverload("reverse", List.of("java.lang.String"))))),
          descriptor.binding().orElseThrow().api());
    }
  }

  @Test
  void generatesTrustedNormSourcesForAnApacheCommonsLangBinding() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("binding-project"));
    Path entry = source(root, "commons/lang/Main.norm", "package commons.lang Void main() {}");
    Path modulePath = root.resolve("commons/lang/module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "commons.lang",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "3.20.0"
              ),
              api: [jarType(name: "StringUtils", members: ["reverse"])]
            )
          )
        }
        """);

    try (ProjectLoader projects =
        environment().projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      ProjectSourceSet sourceSet = projects.load(entry);

      SourceFile generated =
          sourceSet.sources().stream()
              .filter(source -> source.displayName().endsWith("StringUtils.norm"))
              .findFirst()
              .orElseThrow();
      assertTrue(generated.text().contains("stringUtilsReverse"));
      assertFalse(generated.text().contains("stringUtilsAbbreviate"));
      assertEquals(Set.of(generated.id()), sourceSet.bindingSourceDocuments());
      assertTrue(
          sourceSet.sources().stream()
              .noneMatch(source -> source.displayName().endsWith("JavaArrays.norm")));
      assertEquals(1, sourceSet.jarBindings().size());
      assertFalse(sourceSet.inputPaths().contains(generated.path()));
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
