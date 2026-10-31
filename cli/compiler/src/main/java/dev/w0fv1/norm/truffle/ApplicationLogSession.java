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

  dev.w0fv1.norm.logging.DependencyLogs.Destination dependencies(ExecutionState state) {
    return new dev.w0fv1.norm.logging.DependencyLogs.Destination() {
      public boolean enabled(org.slf4j.event.Level level) {
        return output != null
            ? level != org.slf4j.event.Level.TRACE
            : level.toInt()
                >= (state.context().debugLogging()
                        ? org.slf4j.event.Level.DEBUG
                        : org.slf4j.event.Level.INFO)
                    .toInt();
      }

      public void write(
          org.slf4j.event.Level level, String source, String message, Throwable cause) {
        if (cause != null) {
          var trace = new java.io.StringWriter();
          cause.printStackTrace(new java.io.PrintWriter(trace));
          message = message + System.lineSeparator() + trace;
        }
        if (output == null) {
          state.context().error().printf("[%s] %s %s%n", level, source, message);
          return;
        }
        var levelType =
            ((dev.w0fv1.norm.core.CoreType.Function) output.functionType())
                .parameterTypes()
                .getFirst();
        String variant =
            switch (level) {
              case TRACE, DEBUG -> "Debug";
              case INFO -> "Info";
              case WARN -> "Warn";
              case ERROR -> "Error";
            };
        String text = message;
        state
            .callbacks()
            .invoke(
                () ->
                    RuntimeInvocation.invoke(
                        state,
                        output,
                        state.values().enumValue(levelType, variant, java.util.List.of()),
                        source,
                        text));
      }
    };
  }

  void close(ExecutionState state) {
    if (close != null) RuntimeInvocation.invoke(state, close);
  }
}
