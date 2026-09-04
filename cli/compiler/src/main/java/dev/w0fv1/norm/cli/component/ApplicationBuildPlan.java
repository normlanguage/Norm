package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import java.nio.file.Path;
import java.util.Objects;

public record ApplicationBuildPlan(Path output, boolean singleFile) {
  public ApplicationBuildPlan {
    output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
  }

  public static ApplicationBuildPlan from(ProjectSourceSet sourceSet) {
    Objects.requireNonNull(sourceSet, "sourceSet");
    boolean singleFile =
        sourceSet.rootModulePath().isEmpty()
            || sourceSet.rootModulePath().orElseThrow().equals(sourceSet.primaryPath());
    if (singleFile) {
      Path entry = sourceSet.primaryPath();
      return new ApplicationBuildPlan(
          entry.resolveSibling(entry.getFileName().toString() + ".exe"), true);
    }
    var coordinate = sourceSet.scope().coordinate(sourceSet.primarySource().id()).module();
    String artifact = ModuleRepositoryCoordinate.from(coordinate).artifact();
    return new ApplicationBuildPlan(
        sourceSet
            .rootModulePath()
            .orElseThrow()
            .getParent()
            .resolve("build")
            .resolve(artifact + ".exe"),
        false);
  }
}
