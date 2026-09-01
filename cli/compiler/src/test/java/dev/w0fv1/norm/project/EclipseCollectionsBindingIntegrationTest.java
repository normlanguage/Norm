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

final class EclipseCollectionsBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheEclipseCollectionsNarForObjectAndPrimitiveCollections() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module =
        workspace.resolve("java-binding/eclipse-collections/eclipse/collections/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    install(
        workspace,
        repository,
        "org/eclipse/collections/eclipse-collections/13.0.0",
        "eclipse-collections-13.0.0.jar");
    install(
        workspace,
        repository,
        "org/eclipse/collections/eclipse-collections-api/13.0.0",
        "eclipse-collections-api-13.0.0.jar");
    Files.writeString(
        repository.resolve(
            "org/eclipse/collections/eclipse-collections/13.0.0/eclipse-collections-13.0.0.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.eclipse.collections</groupId>
          <artifactId>eclipse-collections</artifactId>
          <version>13.0.0</version>
          <dependencies>
            <dependency>
              <groupId>org.eclipse.collections</groupId>
              <artifactId>eclipse-collections-api</artifactId>
              <version>13.0.0</version>
            </dependency>
          </dependencies>
        </project>
        """);
    Files.writeString(
        repository.resolve(
            "org/eclipse/collections/eclipse-collections-api/13.0.0/eclipse-collections-api-13.0.0.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.eclipse.collections</groupId>
          <artifactId>eclipse-collections-api</artifactId>
          <version>13.0.0</version>
        </project>
        """);

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
            dependencies: [dependency(name: "eclipse.collections", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import eclipse.collections.FastList
        import eclipse.collections.FastListMultimap
        import eclipse.collections.IntArrayList
        import eclipse.collections.UnifiedMap
        import eclipse.collections.UnifiedSet
        import eclipse.collections.fastListNew
        import eclipse.collections.intArrayListNew
        import eclipse.collections.unifiedMapNew
        import eclipse.collections.unifiedSetNew

        Void main() {
          FastList<String?> names = fastListNew<String>()
          names.add("Norm")
          names.add("Java")
          names.add("NAR")
          printLine(names.size())
          printLine(names.get(1) ?? "")
          printLine(names.contains("NAR"))
          FastList<String?>? selected = names.select((value) { value != "Norm" })
          if selected != null {
            printLine(selected.size())
          }
          FastList<Integer?>? lengths =
            names.collect<Integer>((value) {
              Integer? length = (value ?? "").codePointSize()
              length
            })
          if lengths != null {
            printLine(lengths.get(0) ?? 0)
          }
          FastListMultimap<Integer?, String?>? grouped =
            names.groupBy<Integer>((value) {
              Integer? length = (value ?? "").codePointSize()
              length
            })
          if grouped != null {
            printLine(grouped.size())
            printLine(grouped.containsKeyAndValue(arg0: 3, arg1: "NAR"))
          }

          UnifiedSet<String?> unique = unifiedSetNew<String>()
          unique.add("Norm")
          unique.add("Norm")
          printLine(unique.size())

          UnifiedMap<String?, Integer?> lengthByName = unifiedMapNew<String, Integer>()
          lengthByName.put(arg0: "Norm", arg1: 4)
          lengthByName.put(arg0: "Java", arg1: 4)
          printLine(lengthByName.get("Norm") ?? 0)
          printLine(lengthByName.containsKey("Java"))

          IntArrayList numbers = intArrayListNew()
          numbers.add(2)
          numbers.add(3)
          numbers.add(5)
          printLine(numbers.sum())
          printLine(numbers.max())
          printLine(numbers.contains(3))
          IntArrayList? odd = numbers.select((value) { value % 2 == 1 })
          if odd != null {
            printLine(odd.sum())
          }
          printLine(numbers.count((value) { value > 2 }))
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
            "3",
            "Java",
            "true",
            "2",
            "4",
            "3",
            "true",
            "1",
            "4",
            "true",
            "10",
            "5",
            "true",
            "8",
            "2",
            ""),
        output.toString());
  }

  private static void install(Path workspace, Path repository, String coordinate, String fileName)
      throws Exception {
    Path directory = Files.createDirectories(repository.resolve(coordinate));
    Files.copy(
        workspace
            .resolve("java-binding/eclipse-collections/eclipse/collections/lib")
            .resolve(fileName),
        directory.resolve(fileName));
  }
}
