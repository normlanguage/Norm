package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingTask;

final class JarTaskIntrinsicDispatcher {
  private JarTaskIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    IntrinsicOperation operation =
        switch (intrinsic) {
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
    return IntrinsicDispatcher.jarBindingValue(type, task.await(), annotations, execution, null);
  }

  private static Object close(Object value) {
    resource(value).close();
    return null;
  }

  private static ManagedResource resource(Object value) {
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.resource;
    throw new IllegalStateException("JAR task resource host value is unavailable");
  }
}
