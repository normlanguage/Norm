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

final class MicronautTestBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(180)
  void injectsAndReplacesNormBeansInAPureNormMicronautTest() throws Exception {
    Path repository = repositoryRoot().resolve(".tmp/jar-cache");
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      ModulePackager packager = new ModulePackager(projects);
      Path bindings = repositoryRoot().resolve("java-binding");
      for (String module :
          List.of(
              "junit-jupiter/junit/jupiter",
              "micronaut-test-core/micronaut/test/core",
              "micronaut-test-junit5/micronaut/test/junit5",
              "micronaut-inject/micronaut/inject",
              "jakarta-inject/jakarta/inject",
              "micronaut-inject-java/micronaut/inject/processor")) {
        packager.packageModule(bindings.resolve(module).resolve("module.norm"), repository);
      }
    }

    Path module = Files.createDirectories(temporaryDirectory.resolve("application/sample/test"));
    Files.writeString(
        module.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample.test",
            version: 1,
            exports: [],
            dependencies: [
              dependency(name: "junit.jupiter", version: 1),
              dependency(name: "micronaut.test.core", version: 1),
              dependency(name: "micronaut.test.junit5", version: 1),
              dependency(name: "micronaut.inject", version: 1),
              dependency(name: "jakarta.inject", version: 1),
              dependency(name: "micronaut.inject.processor", version: 1)
            ]
          )
        }
        """);
    Path entry = module.resolve("GreetingServiceTest.norm");
    Files.writeString(
        entry,
        """
        package sample.test

        import jakarta.inject.Inject
        import junit.jupiter.api.Test
        import junit.jupiter.api.assertionsAssertEquals
        import junit.jupiter.api.assertionsAssertNotNull
        import micronaut.inject.context.annotation.Prototype
        import micronaut.test.core.annotation.MockBean
        import micronaut.test.junit5.extensions.junit5.annotation.MicronautTest

        interface Greeting {
          String greet(String name)
        }

        @Prototype()
        class GreetingService implements Greeting {
          String greet(String name) {
            return "Hello, " + name
          }
        }

        class TestGreetingService implements Greeting {
          String greet(String name) {
            return "Test, " + name
          }
        }

        @MicronautTest(startApplication: false, transactional: false)
        class GreetingServiceTest {
          @Inject() Greeting? greetingService

          @MockBean(value: Greeting.class)
          Greeting replacement() {
            return TestGreetingService()
          }

          @Test()
          Void injectsTheService() {
            Greeting? service = greetingService
            assertionsAssertNotNull(arg0: service)
            if service != null {
              assertionsAssertEquals(arg0: "Test, Norm", arg1: service.greet("Norm"))
            }
          }
        }
        """);

    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    ProjectTestResult result;
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      result = launcher.test(entry, ExecutionContext.of(new PrintWriter(output)));
    }

    assertTrue(
        result.compilation().isSuccess(), () -> result.compilation().diagnostics().toString());
    assertTrue(result.isSuccess(), () -> result.report().toString());
    assertEquals(1, result.report().orElseThrow().testsFound());
    assertEquals(1, result.report().orElseThrow().testsSucceeded());
    assertEquals("", output.toString());
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
