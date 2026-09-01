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

final class OrgJsonBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheOrgJsonNarForObjectsArraysParsingMutationAndIteration() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/org-json"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/org-json/org/json/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path jsonArtifact = Files.createDirectories(repository.resolve("org/json/json/20260814"));
    Files.copy(
        workspace.resolve("java-binding/org-json/org/json/lib/json-20260814.jar"),
        jsonArtifact.resolve("json-20260814.jar"));
    Files.writeString(
        jsonArtifact.resolve("json-20260814.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.json</groupId>
          <artifactId>json</artifactId>
          <version>20260814</version>
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
            dependencies: [dependency(name: "org.json", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import org.json.JSONArray
        import org.json.JSONObject
        import org.json.jsonArrayNew
        import org.json.jsonObjectNew

        Void main() {
          JSONObject? object = jsonObjectNew("{\\\"name\\\":\\\"Norm\\\",\\\"count\\\":2,\\\"active\\\":true}")
          if object != null {
            printLine(object.getString("name") ?? "missing")
            printLine(object.getInt("count"))
            printLine(object.getBoolean("active"))
            object.put(arg0: "count", arg1: 3)
            object.put(arg0: "language", arg1: "Norm")
            printLine(object.has("language"))
            printLine(object.optString(arg0: "missing", arg1: "fallback") ?? "missing")
            printLine(object.length())
          }

          JSONArray? values = jsonArrayNew("[1,2,3]")
          if values != null {
            values.put("four")
            printLine(values.getInt(1))
            printLine(values.getString(3) ?? "missing")
            Integer seen = 0
            for Any? value : values {
              if value != null {
                seen = seen + 1
              }
            }
            printLine(seen)
            values.remove(0)
            printLine(values.length())
            printLine(values.toString() ?? "missing")
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
            "Norm",
            "2",
            "true",
            "true",
            "fallback",
            "4",
            "2",
            "four",
            "4",
            "3",
            "[2,3,\"four\"]",
            ""),
        output.toString());
  }
}
