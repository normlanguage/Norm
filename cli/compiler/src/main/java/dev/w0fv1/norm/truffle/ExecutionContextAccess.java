package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import dev.w0fv1.norm.execution.ExecutionContext;

final class ExecutionContextAccess {
  private ExecutionContextAccess() {}

  static ExecutionState state(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    if (arguments.length == 0 || !(arguments[0] instanceof ExecutionState state)) {
      throw new IllegalStateException("execution state argument is absent");
    }
    if (state.liveBindings() == null) return state;
    ExecutionContext context = state.liveBindings().context(state.context());
    GuestValueFactory values = state.runtime().values();
    return context == state.context() && values == state.values()
        ? state
        : new ExecutionState(
            context,
            state.annotationExecution(),
            values,
            state.resources(),
            state.callbacks(),
            state.contexts(),
            state.liveBindings());
  }

  static ExecutionContext get(VirtualFrame frame) {
    return state(frame).context();
  }
}
