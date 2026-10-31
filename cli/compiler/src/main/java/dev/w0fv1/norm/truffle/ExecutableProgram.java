package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import java.util.Map;

record ExecutableProgram(
    RootCallTarget entryPoint,
    java.util.Optional<CallTarget> logStartup,
    AnnotationRuntime annotations,
    GuestValueFactory values,
    RuntimeProgram program,
    Map<DefinitionId, CallTarget> bridgeTargets) {
  ExecutionState execution(ExecutionContext context) {
    return execution(context, null);
  }

  ExecutionState execution(ExecutionContext context, LiveBindings bindings) {
    return new ExecutionState(
        context,
        annotations.execution(),
        values,
        new ResourceScope(),
        new GuestCallbackScheduler(),
        new ExecutionContexts(),
        new ApplicationLogSession(),
        bindings);
  }

  Object execute(ExecutionContext context) {
    return execute(context, null);
  }

  Object execute(ExecutionContext context, LiveBindings bindings) {
    ExecutionState state = execution(context, bindings);
    JavaApplicationBridge.Registration bridge = null;
    Throwable failure = null;
    try {
      if (bindings != null) bindings.beginExecution(state);
      if (context.jarBindingRuntime() instanceof JavaApplicationRuntime runtime) {
        if (bindings == null)
          bridge =
              JavaApplicationBridge.install(
                  runtime.applicationClassLoaders(),
                  new JavaApplicationDispatch(program, bridgeTargets, values, state, runtime));
      }
      return dev.w0fv1.norm.logging.DependencyLogs.within(
          state.logs().dependencies(state),
          () -> {
            logStartup.ifPresent(target -> target.call(state));
            if (context.jarBindingRuntime() instanceof JavaApplicationRuntime runtime
                && context.javaApplicationEntrypoint().isPresent()) {
              context
                  .javaApplicationEntrypoint()
                  .orElseThrow()
                  .execute(runtime.applicationClassLoader());
              return null;
            }
            return entryPoint.call(state);
          });
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      try {
        try {
          state.logs().close(state);
        } finally {
          state.close(failure);
        }
      } finally {
        if (bridge != null) bridge.close();
        if (bindings != null) bindings.endExecution();
      }
    }
  }
}
