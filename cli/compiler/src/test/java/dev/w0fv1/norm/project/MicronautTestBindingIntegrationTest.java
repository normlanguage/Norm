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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautTestBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(300)
  void injectsAndReplacesNormBeansInAPureNormMicronautTest() throws Exception {
    Path repository = PublishedPackageCache.path();
    NormRuntime backend = new NormRuntime();

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
              dependency(repository: "github", name: "junit.jupiter", version: 1),
              dependency(repository: "github", name: "micronaut.test.core", version: 1),
              dependency(repository: "github", name: "micronaut.test.junit5", version: 1),
              dependency(repository: "github", name: "micronaut.inject", version: 1),
              dependency(repository: "github", name: "jakarta.inject", version: 1),
              dependency(repository: "github", name: "micronaut.inject.processor", version: 1)
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
}
