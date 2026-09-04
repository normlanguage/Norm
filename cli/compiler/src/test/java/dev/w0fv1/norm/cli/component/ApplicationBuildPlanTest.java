package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApplicationBuildPlanTest {
  @TempDir Path temporaryDirectory;

  @Test
  void placesASingleFileExecutableBesideItsSource() throws Exception {
    Path source = temporaryDirectory.resolve("web.norm");
    Files.writeString(source, "Module module() { return module(dependencies: []) } Void main() {}");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var loader = environment.projectLoader()) {
      ApplicationBuildPlan plan = ApplicationBuildPlan.from(loader.load(source));

      assertTrue(plan.singleFile());
      assertEquals(temporaryDirectory.resolve("web.norm.exe"), plan.output());
    }
  }

  @Test
  void placesAProjectExecutableInItsBuildDirectory() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("hello/web"));
    Path source = module.resolve("application.norm");
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"hello.web\", version: 1) }");
    Files.writeString(source, "package hello.web Void main() {}");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var loader = environment.projectLoader()) {
      ApplicationBuildPlan plan = ApplicationBuildPlan.from(loader.load(source));

      assertFalse(plan.singleFile());
      assertEquals(module.resolve("build/web.exe"), plan.output());
    }
  }
}
