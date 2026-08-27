package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import dev.w0fv1.norm.execution.ExecutionContext;

record ExecutableProgram(RootCallTarget entryPoint, AnnotationRuntime annotations) {
  ExecutionState execution(ExecutionContext context) {
    return new ExecutionState(context, annotations.execution());
  }

  Object execute(ExecutionContext context) {
    return entryPoint.call(execution(context));
  }
}
