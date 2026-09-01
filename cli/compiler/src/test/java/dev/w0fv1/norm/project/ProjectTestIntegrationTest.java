package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectTestIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void runsARealJunitTestWhoseClassAndMethodBodyAreNorm() throws Exception {
    Path junitModule =
        Files.createDirectories(temporaryDirectory.resolve("dependencies/junit/jupiter"));
    Path junitJar = junitModule.resolve("lib/junit-jupiter-api.jar");
    Files.createDirectories(junitJar.getParent());
    Files.copy(junitApiJar(), junitJar, StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(
        junitModule.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "junit.jupiter",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/junit-jupiter-api.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "api.Test", members: [])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(junitJar).value()));

    Path application = Files.createDirectories(temporaryDirectory.resolve("app"));
    Files.writeString(
        application.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "app",
            version: 1,
            exports: [],
            dependencies: [dependency(name: "junit.jupiter", version: 1)]
          )
        }
        """);
    Path test = application.resolve("GreetingTest.norm");
    Files.writeString(
        test,
        """
        package app

        import junit.jupiter.api.Test

        class GreetingTest {
          @Test()
          Void returnsAGreeting() {
            printLine("Hello from a Norm test")
          }
        }
        """);

    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    ProjectTestResult result;
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("maven-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      result = launcher.test(test, ExecutionContext.of(new PrintWriter(output)));
    }

    assertTrue(
        result.compilation().isSuccess(), () -> result.compilation().diagnostics().toString());
    assertTrue(result.isSuccess(), () -> result.report().toString());
    assertEquals(1, result.report().orElseThrow().testsFound());
    assertEquals(1, result.report().orElseThrow().testsSucceeded());
    assertEquals(0, result.report().orElseThrow().testsFailed());
    assertEquals("Hello from a Norm test" + System.lineSeparator(), output.toString());
  }

  private static Path junitApiJar() throws URISyntaxException {
    return Path.of(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  }
}
