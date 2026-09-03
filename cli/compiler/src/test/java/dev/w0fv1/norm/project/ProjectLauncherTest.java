package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLauncherTest {
  @TempDir Path temporaryDirectory;

  @Test
  void runsModuleConfigurationBeforeMainAndAllowsStandardLibraryCalls() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("sample"));
    Path app = Files.createDirectories(root.resolve("sample"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        import std.math.max

        Module module() {
          return module(
            name: "sample",
            version: max(left: 1, right: 0),
            exports: ["Main"]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package sample

        Void main() {
          printLine("started")
        }
        """);
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();

    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher = environment.launcher()) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("started" + System.lineSeparator(), output.toString());
  }

  @Test
  void loadsAStandaloneSourceAsAModuleWhenItDeclaresModule() throws Exception {
    Path source =
        source(
            temporaryDirectory,
            "web.norm",
            """
            package hello.web

            public Module module() {
              return module(name: "hello.web", version: 1)
            }

            Void main() {}
            """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      ProjectSourceSet sourceSet = projects.load(source);

      assertEquals(
          "hello.web",
          sourceSet.scope().coordinate(sourceSet.primarySource().id()).module().name());
      assertTrue(sourceSet.rootModulePath().isPresent());
      assertEquals(source.toAbsolutePath().normalize(), sourceSet.rootModulePath().orElseThrow());
    }
  }

  @Test
  void resolvesARepositoryDependencyDeclaredInTheApplicationSource() throws Exception {
    Path entry =
        source(
            temporaryDirectory,
            "web.norm",
            """
            package hello.web

            import base.answer

            Module module() {
              return module(
                name: "hello.web",
                version: 1,
                dependencies: [
                  dependency(repository: "github", name: "base", version: 1)
                ]
              )
            }

            Void main() {
              printLine(answer().toString())
            }
            """);
    Path dependency = Files.createDirectories(temporaryDirectory.resolve("dependencies/base"));
    Files.writeString(
        dependency.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"]) }");
    source(dependency, "Value.norm", "package base public Integer answer() { return 42 }");
    StringWriter output = new StringWriter();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLauncher launcher = environment.launcher()) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("42" + System.lineSeparator(), output.toString());
  }

  @Test
  void requiresTheModuleFactoryEntryPoint() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("missing"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Path module = root.resolve("sample/module.norm");
    Files.writeString(module, "Void configure() {}");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      var exception = assertThrows(java.io.IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("module"));
    }

    Files.writeString(module, "Void module() {}");
    try (ProjectLoader projects = environment.projectLoader()) {
      var exception = assertThrows(java.io.IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("Module"));
    }
  }

  @Test
  void evaluatesUnsavedModuleConfigurationOverlay() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("overlay"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Path module = root.resolve("sample/module.norm");
    Files.writeString(
        module, "Module module() { return module(name: \"disk\", version: 1, exports: []) }");
    var openModule =
        dev.w0fv1.norm.value.SourceFile.of(
            module,
            "Module module() { return module(name: \"sample\", version: 2, exports: [\"Main\"]) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      var sourceSet =
          projects.load(dev.w0fv1.norm.value.SourceFile.read(entry), List.of(openModule));

      var coordinate = sourceSet.scope().coordinate(sourceSet.primarySource().id()).module();
      assertEquals("sample", coordinate.name());
      assertEquals(2, coordinate.version());
      assertEquals(1, sourceSet.exportedSourcePaths().size());
    }
  }

  @Test
  void rejectsMissingExportedSource() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("missing-export"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"Missing\"]) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      var exception = assertThrows(java.io.IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("sample/Missing.norm"));
    }
  }

  @Test
  void requiresApplicationEntryAndModuleConfigurationToShareADirectory() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("separate-entry"));
    Path entry = source(root, "sample/app/Main.norm", "package sample.app Void main() {}");
    Files.createDirectories(root.resolve("sample"));
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: []) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLauncher launcher = environment.launcher()) {
      IOException exception = assertThrows(IOException.class, () -> launcher.compile(entry));

      assertTrue(exception.getMessage().contains("same directory"));
    }
  }

  @Test
  void resolvesAndCompilesDeclaredModuleDependencies() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("dependency-graph"));
    Path entry =
        source(
            root,
            "sample/Main.norm",
            """
            package sample
            import base.answer
            Void main() { printLine(answer()) }
            """);
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(repository: "github", name: "base", version: 1)]
          )
        }
        """);
    Path dependency = Files.createDirectories(root.resolve("dependencies/base"));
    Files.writeString(
        dependency.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"]) }");
    source(dependency, "Value.norm", "package base public Integer answer() { return 42 }");
    StringWriter output = new StringWriter();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLauncher launcher = environment.launcher()) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("42" + System.lineSeparator(), output.toString());
  }

  @Test
  void resolvesGeneratesAndRunsApacheCommonsLangAsANormModule() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("commons-e2e"));
    Path entry =
        source(
            root,
            "sample/Main.norm",
            """
            package sample
            import commons.lang.stringUtilsReverse

            Void main() {
              printLine(stringUtilsReverse("Norm") ?? "missing")
            }
            """);
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(repository: "github", name: "commons.lang", version: 1)]
          )
        }
        """);
    Path dependency = Files.createDirectories(root.resolve("dependencies/commons/lang"));
    Files.writeString(
        dependency.resolve("module.norm"),
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
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    Path cache = temporaryDirectory.resolve("maven-cache");

    try (ProjectLoader projects = environment.projectLoader(cache)) {
      new ModuleBindingResolutionService(projects).resolve(dependency.resolve("module.norm"));
    }
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(cache), environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("mroN" + System.lineSeparator(), output.toString());
  }

  @Test
  void enforcesDeclaredModuleEdgesDuringImportResolution() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("dependency-visibility"));
    Path entry =
        source(
            root,
            "sample/Main.norm",
            "package sample import base.answer Void main() { printLine(answer()) }");
    source(root, "sample/Root.norm", "package sample public Integer rootValue() { return 1 }");
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main", "Root"],
            dependencies: [
              dependency(repository: "github", name: "middle", version: 1),
              dependency(repository: "github", name: "sibling", version: 1)
            ]
          )
        }
        """);
    Path middle = Files.createDirectories(root.resolve("dependencies/middle"));
    source(
        middle,
        "Value.norm",
        "package middle import sibling.side public Integer middleValue() { return side() }");
    Files.writeString(
        middle.resolve("module.norm"),
        "Module module() { return module(name: \"middle\", version: 1, exports: [\"Value\"], dependencies: [dependency(repository: \"github\", name: \"base\", version: 1)]) }");
    Path base = Files.createDirectories(root.resolve("dependencies/base"));
    source(
        base,
        "Value.norm",
        "package base import sample.rootValue public Integer answer() { return rootValue() }");
    Files.writeString(
        base.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"]) }");
    Path sibling = Files.createDirectories(root.resolve("dependencies/sibling"));
    source(sibling, "Value.norm", "package sibling public Integer side() { return 2 }");
    Files.writeString(
        sibling.resolve("module.norm"),
        "Module module() { return module(name: \"sibling\", version: 1, exports: [\"Value\"]) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLauncher launcher = environment.launcher()) {
      var result = launcher.compile(entry);
      List<String> messages = result.diagnostics().stream().map(value -> value.message()).toList();

      assertTrue(
          messages.stream().anyMatch(value -> value.contains("base.answer")), messages::toString);
      assertTrue(
          messages.stream().anyMatch(value -> value.contains("sibling.side")), messages::toString);
      assertTrue(
          messages.stream().anyMatch(value -> value.contains("sample.rootValue")),
          messages::toString);
    }
  }

  @Test
  void exposesDependenciesReexportedByDirectModules() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("exported-dependency"));
    Path entry =
        source(
            root,
            "sample/Main.norm",
            "package sample import base.answer Void main() { printLine(answer().toString()) }");
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"Main\"], dependencies: [dependency(repository: \"github\", name: \"platform\", version: 1)]) }");
    Path platform = Files.createDirectories(root.resolve("dependencies/platform"));
    Files.writeString(
        platform.resolve("module.norm"),
        "Module module() { return module(name: \"platform\", version: 1, exports: [], dependencies: [exportedDependency(repository: \"github\", name: \"base\", version: 1)]) }");
    Path base = Files.createDirectories(root.resolve("dependencies/base"));
    source(base, "Value.norm", "package base public Integer answer() { return 42 }");
    Files.writeString(
        base.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"]) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLauncher launcher = environment.launcher()) {
      var result = launcher.compile(entry);

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }

  @Test
  void acceptsAUserDefinedModuleImplementation() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("custom-module"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        private class CustomModule implements Module {
          String name() { return "sample" }
          Integer version() { return 1 }
          List<String> exports() { return ["Main"] }
          List<ModuleRequirement> dependencies() { return [] }
          JarBinding? binding() { return null }
        }

        Module module() {
          return CustomModule()
        }
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      var sourceSet = projects.load(entry);

      assertEquals(
          "sample", sourceSet.scope().coordinate(sourceSet.primarySource().id()).module().name());
      assertEquals(1, sourceSet.exportedSourcePaths().size());
    }
  }

  @Test
  void rejectsDependencyCoordinateMismatches() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("dependency-mismatch"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: [],
            dependencies: [dependency(repository: "github", name: "base", version: 2)]
          )
        }
        """);
    Path dependency = Files.createDirectories(root.resolve("dependencies/base"));
    Files.writeString(
        dependency.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: []) }");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("base@2"));
      assertTrue(exception.getMessage().contains("base@1"));
    }
  }

  @Test
  void rejectsModuleDependencyCycles() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("dependency-cycle"));
    Path entry = source(root, "sample/Main.norm", "package sample Void main() {}");
    Files.writeString(
        root.resolve("sample/module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: [],
            dependencies: [dependency(repository: "github", name: "base", version: 1)]
          )
        }
        """);
    Path dependency = Files.createDirectories(root.resolve("dependencies/base"));
    Files.writeString(
        dependency.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "base",
            version: 1,
            exports: [],
            dependencies: [dependency(repository: "github", name: "sample", version: 1)]
          )
        }
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (ProjectLoader projects = environment.projectLoader()) {
      IOException exception = assertThrows(IOException.class, () -> projects.load(entry));

      assertTrue(exception.getMessage().contains("sample@1 -> base@1 -> sample@1"));
    }
  }

  private static Path source(Path root, String relativePath, String text) throws Exception {
    Path path = root.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text);
    return path;
  }
}
