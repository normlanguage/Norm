package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class MicronautBbsExampleIntegrationTest {
  @Test
  @Timeout(300)
  void compilesWithTypedEntityRepositories() throws Exception {
    Path root = Path.of("").toAbsolutePath().normalize();
    while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts"))) {
      root = root.getParent();
    }
    if (root == null) throw new IllegalStateException("repository root is unavailable");
    Path application = root.resolve("docs/examples/micronaut-bbs/app/sample/bbs/application.norm");
    Path repositories = application.getParent().resolve("repository");
    String userRepository = Files.readString(repositories.resolve("UserRepository.norm"));
    String topicRepository = Files.readString(repositories.resolve("TopicRepository.norm"));
    assertTrue(userRepository.contains("extends Repository<UserEntity, Long>"));
    assertTrue(topicRepository.contains("extends Repository<TopicEntity, Long>"));
    assertTrue(!Files.exists(repositories.resolve("AccountRepository.norm")));
    assertTrue(!Files.exists(repositories.resolve("ForumRepository.norm")));

    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(PublishedPackageCache.path()),
            environment.compilerSession(),
            backend)) {
      var result = launcher.compile(application);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }
}
