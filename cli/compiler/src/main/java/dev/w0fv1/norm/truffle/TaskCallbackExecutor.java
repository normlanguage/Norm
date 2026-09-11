package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.FutureBindingTask;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

final class TaskCallbackExecutor implements Executor {
  private final ExecutionState execution;
  private final RuntimeValues.Closure dispatch;
  private final RuntimeValues.Closure registered;
  private final RuntimeValues.Closure released;
  private final RuntimeValues.Closure unhandled;

  TaskCallbackExecutor(
      ExecutionState execution,
      RuntimeValues.Closure dispatch,
      RuntimeValues.Closure registered,
      RuntimeValues.Closure released,
      RuntimeValues.Closure unhandled) {
    this.execution = execution;
    this.dispatch = dispatch;
    this.registered = registered;
    this.released = released;
    this.unhandled = unhandled;
  }

  @Override
  public void execute(Runnable pending) {
    if (dispatch == null) {
      FutureBindingTask.workerExecutor().execute(pending);
      return;
    }
    execution
        .callbacks()
        .invoke(() -> RuntimeInvocation.invoke(execution, dispatch, callback(pending)));
  }

  boolean reportsFailures() {
    return unhandled != null;
  }

  void report(Runnable failure) {
    RuntimeInvocation.invoke(execution, unhandled, callback(failure));
  }

  private RuntimeValues.Closure callback(Runnable pending) {
    var callback =
        new RootNode(null) {
          @Override
          public Object execute(VirtualFrame frame) {
            pending.run();
            return null;
          }
        };
    return new RuntimeValues.Closure(
        callback.getCallTarget(),
        Optional.empty(),
        false,
        null,
        new Object[0],
        new Object[0],
        new Object[0],
        new CoreType.Function(CoreType.VOID, List.of(), CoreNullability.NON_NULL));
  }

  void own(RuntimeValues.OpaqueResource task) {
    RuntimeInvocation.invoke(execution, registered, task);
  }

  void release(RuntimeValues.OpaqueResource task) {
    RuntimeInvocation.invoke(execution, released, task);
  }
}
