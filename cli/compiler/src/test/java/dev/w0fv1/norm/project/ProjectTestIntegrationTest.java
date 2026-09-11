package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.application.ProjectTestResult;
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
  void sharesTheJavaRuntimeWhileIsolatingNormStateBetweenTests() throws Exception {
    Path application = Files.createDirectories(temporaryDirectory.resolve("app"));
    Files.writeString(
        application.resolve("module.norm"),
        "Module module() { module(name: \"app\", version: 1) }");
    Path test =
        Files.writeString(
            application.resolve("Cases.norm"),
            """
            package app
            import std.testing.Test
            import std.annotation.FunctionInterceptor
            import std.annotation.RuntimeRetention
            annotation Once implements FunctionInterceptor, RuntimeRetention {
              private Integer calls
              Once() { calls = 0 }
              Void before(FunctionContext context) {
                calls = calls + 1
                require(condition: calls == 1, message: "annotation state leaked between tests")
              }
            }
            @Once()
            Void work() {}
            @Test
            Void first() { work() }
            @Test
            Void second() { work() }
            """);
    var runtime = new NormRuntime();
    var loaders = new java.util.ArrayList<ClassLoader>();
    dev.w0fv1.norm.execution.ExecutionBackend observed =
        (artifact, plan, context) -> {
          if (dev.w0fv1.norm.core.CoreTestIndex.from(artifact).tests().stream()
              .anyMatch(
                  item -> item.occurrence().representative().equals(artifact.entryDefinition()))) {
            loaders.add(
                ((dev.w0fv1.norm.execution.JavaApplicationRuntime) context.jarBindingRuntime())
                    .applicationClassLoader());
          }
          runtime.execute(artifact, plan, context);
        };
    var environment = ProjectEnvironment.bootstrap(observed);
    ProjectTestResult result;
    try (var runner = ApplicationRunner.open(environment)) {
      result = runner.test(test, ExecutionContext.of(new PrintWriter(new StringWriter())));
    }
    assertTrue(
        result.isSuccess(), () -> result.compilation().diagnostics() + " " + result.report());
    assertEquals(2, result.report().orElseThrow().testsSucceeded());
    assertEquals(2, loaders.size());
    assertSame(loaders.getFirst(), loaders.getLast());
  }

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
            dependencies: [dependency(repository: "github", name: "junit.jupiter", version: 1)]
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
        ApplicationRunner launcher =
            new ApplicationRunner(projects, environment.compilerSession(), backend)) {
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
