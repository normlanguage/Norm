package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class MicronautSingleFileExampleIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @Timeout(300)
  void compilesTheDatabaseBackedSingleFileExample() throws Exception {
    Path root = Path.of("").toAbsolutePath().normalize();
    while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts"))) {
      root = root.getParent();
    }
    if (root == null) throw new IllegalStateException("repository root is unavailable");
    String source =
        Files.readString(root.resolve("docs/examples/micronaut-single-file/web.norm"))
            .replace(
                "dependency(repository: \"github\", name: \"micronaut.web\")",
                "dependency(repository: \"github\", name: \"micronaut.web\", version: 3)")
            .replace(
                "dependency(repository: \"github\", name: \"orm.micronaut.tx\")",
                "dependency(repository: \"github\", name: \"orm.micronaut.tx\", version: 2)")
            .replace(
                "dependency(repository: \"github\", name: \"h2.database\")",
                "dependency(repository: \"github\", name: \"h2.database\", version: 1)");
    assertTrue(source.contains("@Entity()"));
    assertTrue(!source.contains("@Table"));
    assertTrue(!source.contains("import orm.Table"));
    assertTrue(source.contains("class HelloRepository extends Repository<HelloEntity, Long>"));
    assertTrue(source.contains("HelloRepository(RepositoryContext context)"));
    assertTrue(source.contains("super(context)"));
    assertTrue(!source.contains("enum HelloFailure"));
    assertTrue(source.contains("Result<String> hello(String name)"));
    assertTrue(source.contains("Result.Err(\"Name is required\")"));
    assertTrue(source.contains("HelloEntity saved = names.save(HelloEntity(name))"));
    assertTrue(source.contains("Result.Ok(\"Hello, ${saved.name}!\")"));
    assertTrue(!source.contains("names.flush()"));
    assertTrue(!source.contains("names.clear()"));
    assertTrue(!source.contains("names.find("));
    assertTrue(!source.contains("@Context()"));
    assertTrue(!source.contains("Store?"));
    Path entry = temporaryDirectory.resolve("web.norm");
    Files.writeString(entry, source);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);

    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(PublishedPackageCache.path()),
            environment.compilerSession(),
            backend)) {
      var result = launcher.compile(entry);

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }
}
