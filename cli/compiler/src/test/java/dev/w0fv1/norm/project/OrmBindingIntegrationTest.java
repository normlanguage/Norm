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

final class OrmBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(180)
  void persistsAndLoadsANormEntityThroughHibernate() throws Exception {
    Path root = repositoryRoot();
    Path repository = Files.createDirectories(temporaryDirectory.resolve("repository"));
    Path jarCache = root.resolve(".tmp/jar-cache");
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment packagingEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = packagingEnvironment.projectLoader(repository, jarCache)) {
      ModulePackager packager = new ModulePackager(projects);
      packager.packageModule(root.resolve("java-binding/orm-api/orm/module.norm"), repository);
      packager.packageModule(
          root.resolve("java-binding/orm-hibernate/orm/hibernate/module.norm"), repository);
      packager.packageModule(
          root.resolve("java-binding/h2-database/h2/database/module.norm"), repository);
    }

    Path entry = root.resolve("docs/examples/norm-orm/app/sample/orm/Main.norm");
    StringWriter output = new StringWriter();
    ProjectEnvironment applicationEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            applicationEnvironment.projectLoader(repository, jarCache),
            applicationEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("norm:1" + System.lineSeparator(), output.toString());
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
