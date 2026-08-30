package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.execution.ExecutionContext;
import java.util.Objects;

record ExecutionState(
    ExecutionContext context,
    AnnotationRuntime.Execution annotationExecution,
    GuestValueFactory values,
    ResourceScope resources) {
  ExecutionState {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(annotationExecution, "annotationExecution");
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(resources, "resources");
  }

  void close(Throwable failure) {
    try {
      resources.close();
    } catch (ResourceCloseException closeFailure) {
      if (failure == null) throw closeFailure;
      failure.addSuppressed(closeFailure);
    }
  }
}
