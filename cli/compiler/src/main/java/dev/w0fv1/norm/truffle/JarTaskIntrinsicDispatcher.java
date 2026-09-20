package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.FutureBindingTask;
import dev.w0fv1.norm.execution.JarBindingCallbackException;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingTask;

final class JarTaskIntrinsicDispatcher {
  private record CompletionSource(java.util.concurrent.CompletableFuture<Object> future)
      implements AutoCloseable {
    @Override
    public void close() {
      future.cancel(true);
    }
  }

  private JarTaskIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    IntrinsicOperation operation =
        switch (intrinsic) {
          case COMPLETION_CREATE ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  execution
                      .values()
                      .resource(
                          type,
                          new CompletionSource(new java.util.concurrent.CompletableFuture<>()),
                          "Completion",
                          execution);
          case COMPLETION_TASK ->
              (receiver, arguments, type, context, location, annotations, execution) -> {
                var source = resource(arguments[0]).value(CompletionSource.class);
                var task =
                    new FutureBindingTask(
                        source.future(),
                        JarBindingResult.Scalar::new,
                        value ->
                            execution
                                .callbacks()
                                .invoke(
                                    () ->
                                        JavaValueAdapter.jarArgument(
                                            value, execution, annotations)));
                var registration = new TaskRegistration(task, execution);
                var handle =
                    execution.values().resource(type, registration, "Completion task", execution);
                registration.bind(handle);
                return handle;
              };
          case COMPLETION_SUCCEED ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  resource(arguments[0])
                      .value(CompletionSource.class)
                      .future()
                      .complete(RuntimeValues.copy(arguments[1]));
          case COMPLETION_FAIL ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  resource(arguments[0])
                      .value(CompletionSource.class)
                      .future()
                      .completeExceptionally(
                          new JarBindingCallbackException(
                              NormThrownException.create(
                                  (RuntimeValues.ObjectValue) arguments[1], location)));
          case COMPLETION_CLOSE ->
              (receiver, arguments, type, context, location, annotations, execution) -> {
                resource(arguments[0]).close();
                return null;
              };
          case TASK_TERMINATION ->
              (receiver, arguments, type, context, location, annotations, execution) -> {
                var signal = task(arguments[0]).ownedTermination();
                if (signal.isEmpty()) return RuntimeValues.NullValue.INSTANCE;
                var completed =
                    RuntimeInvocation.invoke(
                        execution, (RuntimeValues.Closure) arguments[1], new Object[0]);
                var barrier =
                    new FutureBindingTask(
                        signal.orElseThrow(), ignored -> new JarBindingResult.Scalar(completed));
                var registration = new TaskRegistration(barrier, execution);
                var handle =
                    execution.values().resource(type, registration, "Task termination", execution);
                registration.bind(handle);
                return handle;
              };
          case TASK_START, TASK_CONTINUE ->
              (receiver, arguments, type, context, location, annotations, execution) -> {
                var work =
                    (RuntimeValues.Closure) arguments[intrinsic == IntrinsicId.TASK_START ? 0 : 1];
                java.util.concurrent.Callable<Object> action =
                    () ->
                        execution
                            .callbacks()
                            .invoke(
                                () -> {
                                  try {
                                    return RuntimeInvocation.invoke(execution, work, new Object[0]);
                                  } catch (RuntimeException failure) {
                                    throw new JarBindingCallbackException(failure);
                                  }
                                });
                java.util.function.Function<Object, Object> hostConversion =
                    value ->
                        execution
                            .callbacks()
                            .invoke(
                                () -> JavaValueAdapter.jarArgument(value, execution, annotations));
                FutureBindingTask created;
                if (intrinsic == IntrinsicId.TASK_CONTINUE) {
                  var parent = task(arguments[0]);
                  created =
                      FutureBindingTask.after(
                          parent.completion(),
                          action,
                          JarBindingResult.Scalar::new,
                          hostConversion,
                          parent.continuationExecutor());
                } else {
                  var policy =
                      new TaskCallbackExecutor(
                          execution,
                          arguments[1] instanceof RuntimeValues.Closure dispatch ? dispatch : null,
                          (RuntimeValues.Closure) arguments[2],
                          (RuntimeValues.Closure) arguments[3],
                          arguments[4] instanceof RuntimeValues.Closure unhandled
                              ? unhandled
                              : null);
                  created =
                      FutureBindingTask.start(
                          action, JarBindingResult.Scalar::new, hostConversion, policy);
                }
                var registration = new TaskRegistration(created, execution);
                var handle =
                    execution.values().resource(type, registration, "Norm Task", execution);
                registration.bind(handle);
                return handle;
              };
          case JAR_TASK_AWAIT ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  await(arguments[0], type, annotations, execution, location);
          case JAR_TASK_CANCEL ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  task(arguments[0]).cancel();
          case JAR_TASK_COMPLETED ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  task(arguments[0]).completed();
          case JAR_TASK_CLOSE ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  close(arguments[0]);
          default -> throw new IllegalStateException("unsupported JAR task intrinsic " + intrinsic);
        };
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      if (execution == null) throw new IllegalStateException("JAR task runtime is unavailable");
      try {
        return operation.execute(
            receiver, arguments, type, context, location, annotations, execution);
      } catch (JarBindingInvocationException failure) {
        throw execution.values().javaException(failure.failure(), execution, location);
      } catch (ResourceCloseException failure) {
        throw execution.values().javaException(failure.getCause(), execution, location);
      }
    };
  }

  private static JarBindingTask task(Object value) {
    return resource(value).value(JarBindingTask.class);
  }

  @TruffleBoundary
  private static Object await(
      Object value,
      CoreType type,
      AnnotationRuntime annotations,
      ExecutionState execution,
      Node location) {
    JarBindingTask task = task(value);
    execution.runCallbacksUntil(task::completed, location);
    return JavaValueAdapter.jarBindingValue(type, task.await(), annotations, execution, null);
  }

  private static Object close(Object value) {
    var resource = resource(value);
    try {
      resource.value(JarBindingTask.class).close();
    } finally {
      resource.closedExternally();
    }
    return null;
  }

  private static ManagedResource resource(Object value) {
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.resource;
    throw new IllegalStateException("JAR task resource host value is unavailable");
  }
}
