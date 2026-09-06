package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.PreparedExecution;
import java.util.Objects;

record PreparedTruffleProgram(RuntimeSourceMap locations, ExecutableProgram executable)
    implements PreparedExecution {
  PreparedTruffleProgram {
    Objects.requireNonNull(locations, "locations");
    Objects.requireNonNull(executable, "executable");
  }

  @Override
  public void execute(ExecutionContext context) {
    Objects.requireNonNull(context, "context");
    try {
      executable.execute(context);
    } catch (NormGuestException exception) {
      throw TruffleExecutionBackend.translate(exception, locations);
    } finally {
      context.output().flush();
    }
  }
}
