package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import dev.w0fv1.norm.execution.ExecutionContext;

final class ExecutionContextAccess {
  private ExecutionContextAccess() {}

  static ExecutionContext get(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    if (arguments.length == 0 || !(arguments[0] instanceof ExecutionContext context)) {
      throw new IllegalStateException("execution context argument is absent");
    }
    return context;
  }
}
