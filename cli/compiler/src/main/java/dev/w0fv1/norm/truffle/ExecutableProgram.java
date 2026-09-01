package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import java.util.Map;

record ExecutableProgram(
    RootCallTarget entryPoint,
    AnnotationRuntime annotations,
    GuestValueFactory values,
    CoreProgram program,
    Map<DefinitionId, CallTarget> targets) {
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
    JavaApplicationBridge.Registration bridge = null;
    Throwable failure = null;
    try {
      if (context.jarBindingRuntime() instanceof JavaApplicationRuntime runtime) {
        bridge =
            JavaApplicationBridge.install(
                runtime.applicationClassLoader(),
                new JavaApplicationDispatch(
                    program, targets, values, state, runtime.applicationClassLoader()));
        if (context.javaApplicationEntrypoint().isPresent()) {
          context
              .javaApplicationEntrypoint()
              .orElseThrow()
              .execute(runtime.applicationClassLoader());
          return null;
        }
      }
      return entryPoint.call(state);
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      if (bridge != null) bridge.close();
      state.close(failure);
    }
  }
}
