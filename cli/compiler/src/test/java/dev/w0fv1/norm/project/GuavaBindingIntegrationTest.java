package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuavaBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheGuavaNarForStringsCollectionsAndHashing() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/guava"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/guava/guava/core/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    install(workspace, repository, "com/google/guava/guava/33.7.1-jre", "guava-33.7.1-jre.jar");
    install(
        workspace, repository, "com/google/guava/failureaccess/1.0.3", "failureaccess-1.0.3.jar");
    install(
        workspace,
        repository,
        "com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava",
        "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar");
    install(workspace, repository, "org/jspecify/jspecify/1.0.1", "jspecify-1.0.1.jar");
    install(
        workspace,
        repository,
        "com/google/errorprone/error_prone_annotations/2.50.0",
        "error_prone_annotations-2.50.0.jar");
    install(
        workspace,
        repository,
        "com/google/j2objc/j2objc-annotations/3.1",
        "j2objc-annotations-3.1.jar");
    Files.writeString(
        repository.resolve("com/google/guava/guava/33.7.1-jre/guava-33.7.1-jre.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.google.guava</groupId>
          <artifactId>guava</artifactId>
          <version>33.7.1-jre</version>
          <dependencies>
            <dependency><groupId>com.google.guava</groupId><artifactId>failureaccess</artifactId><version>1.0.3</version></dependency>
            <dependency><groupId>com.google.guava</groupId><artifactId>listenablefuture</artifactId><version>9999.0-empty-to-avoid-conflict-with-guava</version></dependency>
            <dependency><groupId>org.jspecify</groupId><artifactId>jspecify</artifactId><version>1.0.1</version></dependency>
            <dependency><groupId>com.google.errorprone</groupId><artifactId>error_prone_annotations</artifactId><version>2.50.0</version></dependency>
            <dependency><groupId>com.google.j2objc</groupId><artifactId>j2objc-annotations</artifactId><version>3.1</version></dependency>
          </dependencies>
        </project>
        """);
    writePom(
        repository,
        "com/google/guava/failureaccess/1.0.3",
        "com.google.guava",
        "failureaccess",
        "1.0.3");
    writePom(
        repository,
        "com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava",
        "com.google.guava",
        "listenablefuture",
        "9999.0-empty-to-avoid-conflict-with-guava");
    writePom(repository, "org/jspecify/jspecify/1.0.1", "org.jspecify", "jspecify", "1.0.1");
    writePom(
        repository,
        "com/google/errorprone/error_prone_annotations/2.50.0",
        "com.google.errorprone",
        "error_prone_annotations",
        "2.50.0");
    writePom(
        repository,
        "com/google/j2objc/j2objc-annotations/3.1",
        "com.google.j2objc",
        "j2objc-annotations",
        "3.1");

    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "app",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "guava.core", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import guava.core.HashCode
        import guava.core.HashFunction
        import guava.core.ImmutableList
        import guava.core.ImmutableMap
        import guava.core.ImmutableSet
        import guava.core.Joiner
        import guava.core.ArrayListMultimap
        import guava.core.Splitter
        import guava.core.arrayListMultimapCreate
        import guava.core.hashingSha256
        import guava.core.immutableListOf
        import guava.core.immutableMapOf
        import guava.core.immutableSetOf
        import guava.core.joinerOn
        import guava.core.preconditionsCheckArgument
        import guava.core.splitterOn
        import guava.core.stringsCommonPrefix
        import guava.core.stringsPadStart
        import std.collections.IterableView

        Void main() {
          preconditionsCheckArgument(true)
          printLine(stringsPadStart(arg0: "7", arg1: 3, arg2: '0') ?? "")
          printLine(stringsCommonPrefix(arg0: "Norm", arg1: "Normal") ?? "")

          Splitter? splitter = splitterOn(",")
          Joiner? joiner = joinerOn("|")
          if splitter != null && joiner != null {
            Splitter? configured = splitter.trimResults()?.omitEmptyStrings()
            if configured != null {
              IterableView<String?>? values = configured.split("alpha, ,beta")
              if values != null {
                printLine(joiner.join(values) ?? "")
              }
            }
          }

          ImmutableList<String?>? names = immutableListOf<String>(arg0: "Norm", arg1: "Java", arg2: "NAR")
          if names != null {
            printLine(names.contains("Java"))
            printLine(names.indexOf("NAR"))
            ImmutableList<String?>? reversed = names.reverse()
            if reversed != null {
              printLine(reversed.indexOf("Norm"))
            }
          }

          ImmutableSet<String?>? unique = immutableSetOf<String>(arg0: "Norm", arg1: "Java", arg2: "Norm")
          if unique != null {
            printLine(unique.contains("Java"))
          }
          ImmutableMap<String?, Integer?>? lengths =
            immutableMapOf<String, Integer>(arg0: "Norm", arg1: 4, arg2: "Java", arg3: 4)
          if lengths != null {
            printLine(lengths.get("Norm") ?? 0)
          }
          ArrayListMultimap<String?, Integer?>? groups = arrayListMultimapCreate<String, Integer>()
          if groups != null {
            printLine(groups.put(arg0: "language", arg1: 1))
            printLine(groups.put(arg0: "language", arg1: 2))
            printLine(groups.size())
            printLine(groups.containsEntry(arg0: "language", arg1: 2))
          }

          HashFunction? sha = hashingSha256()
          if sha != null {
            HashCode? hash = sha.hashUnencodedChars("Norm")
            if hash != null {
              printLine(hash.bits())
              printLine(hash.toString() ?? "")
            }
          }
        }
        """);
    StringWriter output = new StringWriter();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        String.join(
            System.lineSeparator(),
            "007",
            "Norm",
            "alpha|beta",
            "true",
            "2",
            "2",
            "true",
            "4",
            "true",
            "true",
            "2",
            "true",
            "256",
            "90c71c71b83553ccb072f98999d777acb6c6295b0e3a779edb83c30959b1a80b",
            ""),
        output.toString());
  }

  private static void install(Path workspace, Path repository, String coordinate, String fileName)
      throws Exception {
    Path directory = Files.createDirectories(repository.resolve(coordinate));
    Files.copy(
        workspace.resolve("java-binding/guava/guava/core/lib").resolve(fileName),
        directory.resolve(fileName));
  }

  private static void writePom(
      Path repository, String coordinate, String group, String artifact, String version)
      throws Exception {
    Files.writeString(
        repository.resolve(coordinate).resolve(artifact + "-" + version + ".pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
        </project>
        """
            .formatted(group, artifact, version));
  }
}
