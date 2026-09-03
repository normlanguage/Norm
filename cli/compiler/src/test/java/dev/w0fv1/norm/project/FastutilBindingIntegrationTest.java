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

final class FastutilBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheFastutilNarForPrimitiveListsSetsMapsAndIteration() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/fastutil"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/fastutil/fastutil/collections/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path fastutilArtifact =
        Files.createDirectories(repository.resolve("it/unimi/dsi/fastutil/8.5.19"));
    Files.copy(
        workspace.resolve("java-binding/fastutil/fastutil/collections/lib/fastutil-8.5.19.jar"),
        fastutilArtifact.resolve("fastutil-8.5.19.jar"));
    Files.writeString(
        fastutilArtifact.resolve("fastutil-8.5.19.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>it.unimi.dsi</groupId>
          <artifactId>fastutil</artifactId>
          <version>8.5.19</version>
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
            dependencies: [dependency(repository: "github", name: "fastutil.collections", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import fastutil.collections.Int2ObjectOpenHashMap
        import fastutil.collections.IntArrayList
        import fastutil.collections.IntOpenHashSet
        import fastutil.collections.Long2ObjectOpenHashMap
        import fastutil.collections.Object2IntOpenHashMap
        import fastutil.collections.int2ObjectOpenHashMapNew
        import fastutil.collections.intArrayListNew
        import fastutil.collections.intOpenHashSetNew
        import fastutil.collections.long2ObjectOpenHashMapNew
        import fastutil.collections.object2IntOpenHashMapNew

        Void main() {
          IntArrayList values = intArrayListNew()
          values.add(4)
          values.add(9)
          values.add(arg0: 1, arg1: 7)
          printLine(values.getInt(1))
          printLine(values.set(arg0: 1, arg1: 8))
          printLine(values.removeInt(0))
          printLine(values.size())
          Integer total = 0
          for Integer? value : values {
            total = total + (value ?? 0)
          }
          printLine(total)

          IntOpenHashSet unique = intOpenHashSetNew()
          printLine(unique.add(5))
          printLine(unique.add(5))
          printLine(unique.contains(5))
          printLine(unique.remove(5))
          printLine(unique.contains(5))

          Int2ObjectOpenHashMap<String?> labels = int2ObjectOpenHashMapNew<String>()
          labels.put(arg0: 42, arg1: "answer")
          printLine(labels.get(42) ?? "missing")
          printLine(labels.putIfAbsent(arg0: 42, arg1: "other") ?? "missing")
          printLine(labels.getOrDefault(arg0: 7, arg1: "seven") ?? "missing")

          Object2IntOpenHashMap<String?> counts = object2IntOpenHashMapNew<String>()
          printLine(counts.put(arg0: "hits", arg1: 2))
          printLine(counts.addTo(arg0: "hits", arg1: 3))
          printLine(counts.getInt("hits"))

          Long2ObjectOpenHashMap<String?> ids = long2ObjectOpenHashMapNew<String>()
          ids.put(arg0: 1000000000, arg1: "large")
          printLine(ids.get(1000000000) ?? "missing")
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
            "7",
            "7",
            "4",
            "2",
            "17",
            "true",
            "false",
            "true",
            "true",
            "false",
            "answer",
            "answer",
            "seven",
            "0",
            "2",
            "5",
            "large",
            ""),
        output.toString());
  }
}
