package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import dev.w0fv1.norm.execution.ExecutionContext;

record ExecutableProgram(
    RootCallTarget entryPoint, AnnotationRuntime annotations, GuestValueFactory values) {
  ExecutionState execution(ExecutionContext context) {
    return new ExecutionState(
        context,
        annotations.execution(),
        values,
        new ResourceScope(),
        new GuestCallbackScheduler());
  }

  Object execute(ExecutionContext context) {
    ExecutionState state = execution(context);
    Throwable failure = null;
    try {
      return entryPoint.call(state);
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      state.close(failure);
    }
  }
}
