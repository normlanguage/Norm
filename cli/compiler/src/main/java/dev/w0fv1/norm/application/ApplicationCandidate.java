package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import java.util.Optional;

record ApplicationCandidate(
    CompilationResult result, ProjectInputSnapshot inputs, Optional<ExecutionRevision> revision)
    implements AutoCloseable {
  ApplicationCandidate {
    if (result.isSuccess() != revision.isPresent())
      throw new IllegalArgumentException("successful application requires a runtime revision");
  }

  @Override
  public void close() {
    revision.ifPresent(ExecutionRevision::close);
  }
}
