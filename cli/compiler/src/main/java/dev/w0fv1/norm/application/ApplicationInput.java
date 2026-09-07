package dev.w0fv1.norm.application;

import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.util.Objects;
import java.util.Optional;

public record ApplicationInput(CompilationRequest request, Optional<ProjectSourceSet> project) {
  public ApplicationInput {
    Objects.requireNonNull(request, "request");
    project = Objects.requireNonNull(project, "project");
  }

  public SourceFile source() {
    return project
        .map(ProjectSourceSet::primarySource)
        .orElseGet(
            () ->
                request.sources().stream()
                    .filter(source -> source.id().equals(request.entryDocument()))
                    .findFirst()
                    .orElseThrow());
  }
}
