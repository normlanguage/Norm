package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingTask;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

final class TaskRegistration implements JarBindingTask {
  private final JarBindingTask task;
  private final ExecutionState execution;
  private final TaskCallbackExecutor owner;
  private RuntimeValues.OpaqueResource handle;
  private boolean registered;
  private boolean released;
  private volatile boolean observed;

  TaskRegistration(JarBindingTask task, ExecutionState execution) {
    this.task = task;
    this.execution = execution;
    this.owner = task.continuationExecutor() instanceof TaskCallbackExecutor policy ? policy : null;
  }

  void bind(RuntimeValues.OpaqueResource handle) {
    this.handle = handle;
    try {
      if (owner != null) {
        owner.own(handle);
        registered = true;
      }
      task.completion()
          .whenComplete(
              (value, failure) -> {
                finish();
                if (failure != null
                    && !(failure instanceof java.util.concurrent.CancellationException)
                    && !observed
                    && owner != null
                    && owner.reportsFailures()) {
                  owner.execute(
                      () -> {
                        if (observed) return;
                        observed = true;
                        owner.report(
                            () -> {
                              try {
                                task.await();
                              } catch (
                                  dev.w0fv1.norm.execution.JarBindingInvocationException error) {
                                throw execution
                                    .values()
                                    .javaException(error.failure(), execution, null);
                              }
                            });
                      });
                }
              });
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  @Override
  public Executor continuationExecutor() {
    return task.continuationExecutor();
  }

  RuntimeValues.OpaqueResource handle() {
    return handle;
  }

  @Override
  public java.util.Optional<CompletionStage<Void>> ownedTermination() {
    return task.ownedTermination();
  }

  @Override
  public CompletionStage<JarBindingResult> completion() {
    observed = true;
    return task.completion();
  }

  @Override
  public JarBindingResult await() {
    observed = true;
    try {
      return task.await();
    } finally {
      if (task.completed()) close();
    }
  }

  @Override
  public boolean cancel() {
    observed = true;
    boolean cancelled = task.cancel();
    if (task.completed()) close();
    return cancelled;
  }

  @Override
  public boolean completed() {
    return task.completed();
  }

  @Override
  public Object hostValue() {
    observed = true;
    return task.hostValue();
  }

  @Override
  public void close() {
    observed = true;
    finish();
  }

  private void finish() {
    execution
        .callbacks()
        .invoke(
            () -> {
              if (released) return null;
              released = true;
              try {
                task.close();
                if (registered) owner.release(handle);
              } finally {
                if (handle != null) handle.resource.closedExternally();
              }
              return null;
            });
  }
}
