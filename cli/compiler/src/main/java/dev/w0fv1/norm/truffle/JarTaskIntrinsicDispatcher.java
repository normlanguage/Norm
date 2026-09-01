package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingTask;

final class JarTaskIntrinsicDispatcher {
  private JarTaskIntrinsicDispatcher() {}

  static Object execute(
      IntrinsicId intrinsic,
      Object value,
      CoreType type,
      AnnotationRuntime annotations,
      ExecutionState execution,
      Node location) {
    if (execution == null) throw new IllegalStateException("JAR task runtime is unavailable");
    try {
      return switch (intrinsic) {
        case JAR_TASK_AWAIT -> await(value, type, annotations, execution, location);
        case JAR_TASK_CANCEL -> task(value).cancel();
        case JAR_TASK_COMPLETED -> task(value).completed();
        case JAR_TASK_CLOSE -> close(value);
        default -> throw new IllegalStateException("unsupported JAR task intrinsic " + intrinsic);
      };
    } catch (JarBindingInvocationException failure) {
      throw execution.values().javaException(failure.failure(), execution, location);
    } catch (ResourceCloseException failure) {
      throw execution.values().javaException(failure.getCause(), execution, location);
    }
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
