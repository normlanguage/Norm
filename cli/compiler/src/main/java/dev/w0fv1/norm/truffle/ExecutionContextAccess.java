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
    return state;
  }

  static ExecutionContext get(VirtualFrame frame) {
    return state(frame).context();
  }
}
