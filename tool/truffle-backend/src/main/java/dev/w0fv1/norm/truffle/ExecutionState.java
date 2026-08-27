package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.execution.ExecutionContext;
import java.util.Objects;

record ExecutionState(ExecutionContext context, AnnotationRuntime.Execution annotationExecution) {
  ExecutionState {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(annotationExecution, "annotationExecution");
  }
}
