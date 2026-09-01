package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautAopSerdeBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(180)
  void runsAnOfficialMicronautAopProxyFromNorm() throws Exception {
    Path repository =
        packageModules(
            List.of(
                "micronaut-core/micronaut/core",
                "micronaut-inject/micronaut/inject",
                "micronaut-aop/micronaut/aop",
                "jakarta-inject/jakarta/inject",
                "micronaut-inject-java/micronaut/inject/processor"));

    String output =
        run(
            repository,
            repositoryRoot().resolve("java-binding/micronaut-aop/sample/sample/aop/Main.norm"));

    assertEquals("AOP:Norm" + System.lineSeparator(), output);
  }

  @Test
  @Timeout(180)
  void runsOfficialMicronautSerdeProcessingAndRoundTripFromNorm() throws Exception {
    Path repository =
        packageModules(
            List.of(
                "micronaut-core/micronaut/core",
                "micronaut-inject/micronaut/inject",
                "micronaut-json/micronaut/json",
                "micronaut-serde-api/micronaut/serde/api",
                "micronaut-serde-jackson/micronaut/serde/jackson",
                "micronaut-serde-processor/micronaut/serde/processor",
                "micronaut-inject-java/micronaut/inject/processor"));

    String output =
        run(
            repository,
            repositoryRoot()
                .resolve("java-binding/micronaut-serde-jackson/sample/sample/serde/Main.norm"));

    assertEquals(String.join(System.lineSeparator(), "{\"text\":\"Norm\"}", "Norm", ""), output);
  }

  private Path packageModules(List<String> modules) throws Exception {
    Path repository = Files.createDirectories(temporaryDirectory.resolve("repository"));
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects =
        environment.projectLoader(repositoryRoot().resolve(".tmp/jar-cache"))) {
      ModulePackager packager = new ModulePackager(projects);
      Path bindings = repositoryRoot().resolve("java-binding");
      for (String module : modules) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }
    return repository;
  }

  private static String run(Path repository, Path entry) throws Exception {
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(repository, repositoryRoot().resolve(".tmp/jar-cache")),
            environment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    return output.toString();
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
      current = current.getParent();
    }
    if (current == null) throw new IllegalStateException("Norm repository root is absent");
    return current;
  }
}
