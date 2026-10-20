package dev.w0fv1.norm.pyrun;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("external")
final class PythonRuntimeOpenSourceE2ETest {
  @TempDir Path temporaryDirectory;

  @Test
  void runsPypaSampleProjectThroughNormAndManagedPython() throws Exception {
    Path project = temporaryDirectory.resolve("sampleproject");
    run(
        List.of(
            "git",
            "clone",
            "--depth",
            "1",
            "https://github.com/pypa/sampleproject.git",
            project.toString()),
        temporaryDirectory);
    Files.writeString(project.resolve("requirements.txt"), "-e .\n");
    Files.writeString(project.resolve("pyrun_smoke.py"), "import sample\nsample.main()\n");

    PythonRuntimePlatform platform = PythonRuntimePlatform.current();
    PythonRuntimeDistribution distribution = PythonRuntimeCatalog.current().distribution(platform);
    Path runtime =
        new PythonRuntimeInstaller(
                distribution,
                temporaryDirectory.resolve("runtimes"),
                PythonRuntimeInstaller::downloadHttp)
            .install(message -> {});
    Path environment =
        new PythonEnvironmentManager(platform)
            .ensure(
                runtime,
                temporaryDirectory.resolve("venv"),
                project.resolve("requirements.txt"),
                message -> {});

    Path app = Files.createDirectories(temporaryDirectory.resolve("norm-app"));
    Path dependency = Files.createDirectories(app.resolve("dependencies/pyrun"));
    Path library = repositoryRoot().resolve("norm/libraries/pyrun");
    try (var files = Files.list(library)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".norm")).toList())
        Files.copy(file, dependency.resolve(file.getFileName()));
    }
    Files.writeString(
        app.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, dependencies: [dependency(repository: \"local\", name: \"pyrun\", version: 1)], exports: []) }");
    Path entry = app.resolve("main.norm");
    Files.writeString(
        entry,
        "package sample\n\nimport pyrun.PythonEntry\nimport pyrun.PythonRequest\nimport pyrun.runPython\nimport std.core.Exception\nimport std.filesystem.Path\n\nVoid main() {\n  var result = runPython(PythonRequest(entry: PythonEntry.Script(path: \"pyrun_smoke.py\"), interpreter: \""
            + escape(environment)
            + "\", directory: Path(value: \""
            + escape(project)
            + "\")))\n  if result.exitCode != 0 { throw Exception(message: \"sampleproject failed\") }\n  printLine(\"SAMPLEPROJECT_OK\")\n}\n");

    StringWriter output = new StringWriter();
    try (ProjectEnvironment environmentModel = ProjectEnvironment.bootstrap(new NormRuntime());
        ApplicationRunner runner = ApplicationRunner.open(environmentModel)) {
      var result =
          runner.run(
              entry,
              ExecutionContext.of(
                  new PrintWriter(output),
                  JdkSystemPlatform.builder().workingDirectory(app).build()));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertTrue(output.toString().contains("SAMPLEPROJECT_OK"), output.toString());
  }

  private static String escape(Path path) {
    return path.toAbsolutePath().toString().replace("\\", "/").replace("\"", "\\\"");
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.isDirectory(current.resolve("norm/libraries/pyrun"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Norm repository root is unavailable");
  }

  private static void run(java.util.List<String> command, Path directory)
      throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
    assertTrue(process.waitFor() == 0, () -> "command failed: " + command);
  }
}
