package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;

final class ApplicationLogSession {
  private RuntimeValues.Closure output;
  private RuntimeValues.Closure close;

  void bind(RuntimeValues.Closure output, RuntimeValues.Closure close) {
    if (this.output != null) throw new IllegalStateException("application logs already active");
    this.output = output;
    this.close = close;
  }

  void emit(ExecutionState state, Object level, String message) {
    String source =
        Truffle.getRuntime()
            .iterateFrames(
                frame ->
                    frame.getCallTarget() instanceof RootCallTarget target
                            && target.getRootNode() instanceof FunctionRootNode function
                        ? function.packageName()
                        : null,
                1);
    RuntimeInvocation.invoke(state, output, level, source == null ? "" : source, message);
  }

  void close(ExecutionState state) {
    if (close != null) RuntimeInvocation.invoke(state, close);
  }
}
