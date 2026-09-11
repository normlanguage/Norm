package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.JarBindingCallback;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.execution.JavaApplicationRuntime;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class JavaIntrinsicDispatcher {
  private JavaIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case JAVA_COLLECTION_SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return javaCollection(first).size();
          };
      case JAVA_LIST_GET ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java list element type is unavailable");
            }
            List<Object> values = javaList(first);
            return JavaValueAdapter.jarValue(
                type,
                values.get(CollectionBounds.index(second, values.size(), location)),
                execution);
          };
      case JAVA_LIST_SET ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            Object third = arguments.length <= 2 ? null : arguments[2];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java list element type is unavailable");
            }
            List<Object> values = javaList(first);
            Object previous =
                values.set(
                    CollectionBounds.index(second, values.size(), location),
                    JavaValueAdapter.jarArgument(third, execution));
            return JavaValueAdapter.jarValue(type, previous, execution);
          };
      case JAVA_LIST_REMOVE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java list element type is unavailable");
            }
            List<Object> values = javaList(first);
            Object removed = values.remove(CollectionBounds.index(second, values.size(), location));
            return JavaValueAdapter.jarValue(type, removed, execution);
          };
      case JAVA_COLLECTION_CONTAINS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return javaCollection(first).contains(JavaValueAdapter.jarArgument(second, execution));
          };
      case JAVA_COLLECTION_ADD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return javaCollection(first).add(JavaValueAdapter.jarArgument(second, execution));
          };
      case JAVA_COLLECTION_REMOVE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return javaCollection(first).remove(JavaValueAdapter.jarArgument(second, execution));
          };
      case JAVA_ITERABLE_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java iterator result type is unavailable");
            }
            Iterator<?> iterator = javaIterable(first).iterator();
            return execution.values().opaque(type, iterator, iterator.getClass().getName());
          };
      case JAVA_ITERATOR_HAS_NEXT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return javaIterator(first).hasNext();
          };
      case JAVA_ITERATOR_NEXT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java iterator element type is unavailable");
            }
            return JavaValueAdapter.jarValue(type, javaIterator(first).next(), execution);
          };
      case JAVA_MAP_NEW ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (execution == null || type == null) {
              throw new IllegalStateException("Java map result type is unavailable");
            }
            return execution.values().opaque(type, new java.util.LinkedHashMap<>(), "MutableMap");
          };
      case JAVA_MAP_SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return javaMap(first).size();
          };
      case JAVA_MAP_CONTAINS_KEY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return javaMap(first).containsKey(JavaValueAdapter.jarArgument(second, execution));
          };
      case JAVA_MAP_GET ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java map value type is unavailable");
            }
            return JavaValueAdapter.jarValue(
                type,
                javaMap(first).get(JavaValueAdapter.jarArgument(second, execution)),
                execution);
          };
      case JAVA_MAP_PUT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            Object third = arguments.length <= 2 ? null : arguments[2];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java map value type is unavailable");
            }
            Object previous =
                javaMap(first)
                    .put(
                        JavaValueAdapter.jarArgument(second, execution),
                        JavaValueAdapter.jarArgument(third, execution));
            return JavaValueAdapter.jarValue(type, previous, execution);
          };
      case JAVA_MAP_REMOVE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (execution == null || type == null) {
              throw new IllegalStateException("Java map value type is unavailable");
            }
            return JavaValueAdapter.jarValue(
                type,
                javaMap(first).remove(JavaValueAdapter.jarArgument(second, execution)),
                execution);
          };
      case JAR_INVOKE, JAR_INVOKE_VOID ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            try {
              List<Object> jarArguments =
                  java.util.Arrays.stream(arguments, 1, arguments.length)
                      .map(value -> JavaValueAdapter.jarArgument(value, execution, annotations))
                      .toList();
              JarBindingResult result;
              try {
                result = invokeJar(context, (String) first, jarArguments, execution, location);
              } finally {
                synchronizeJarArguments(context, jarArguments);
              }
              return JavaValueAdapter.jarBindingValue(type, result, annotations, execution, second);
            } catch (JarBindingInvocationException exception) {
              if (execution == null) {
                throw new IllegalStateException(
                    "JAR invocation exception execution is unavailable");
              }
              throw execution.values().javaException(exception.failure(), execution, location);
            } catch (JarBindingRuntimeException exception) {
              throw new NormGuestException(
                  RuntimeErrorCode.JAR_BINDING, exception.getMessage(), location);
            }
          };
      default -> throw new IllegalArgumentException("Unsupported java intrinsic: " + intrinsic);
    };
  }

  @TruffleBoundary
  private static JarBindingResult invokeJar(
      ExecutionContext context,
      String call,
      List<Object> arguments,
      ExecutionState execution,
      Node location) {
    boolean callbackPumpRequired =
        arguments.stream().anyMatch(JarBindingCallback.class::isInstance)
            || context.jarBindingRuntime() instanceof JavaApplicationRuntime;
    if (execution == null) {
      return context.jarBindingRuntime().invoke(call, arguments);
    }
    if (!callbackPumpRequired || execution.callbacks().isBorrowedExecution()) {
      return execution
          .callbacks()
          .hostCall(() -> context.jarBindingRuntime().invoke(call, arguments));
    }
    CompletableFuture<JarBindingResult> result = new CompletableFuture<>();
    Thread worker =
        Thread.ofVirtual()
            .name("norm-java-binding")
            .start(
                () -> {
                  try {
                    result.complete(context.jarBindingRuntime().invoke(call, arguments));
                  } catch (RuntimeException | Error failure) {
                    result.completeExceptionally(failure);
                  }
                });
    try {
      execution.runCallbacksUntil(result::isDone, location);
      return result.join();
    } catch (CompletionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException exception) throw exception;
      if (cause instanceof Error error) throw error;
      throw new IllegalStateException("JAR binding callback invocation failed", cause);
    } catch (RuntimeException | Error failure) {
      worker.interrupt();
      throw failure;
    }
  }

  private static void synchronizeJarArguments(ExecutionContext context, List<Object> arguments) {
    if (!(context.jarBindingRuntime() instanceof JavaApplicationRuntime runtime)) return;
    for (Object argument : arguments) {
      if (argument != null) {
        JavaApplicationBridge.fromJava(runtime.applicationClassLoader(), argument);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> javaList(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof List<?> list)) {
      throw new IllegalStateException("Java mutable list host value is unavailable");
    }
    return (List<Object>) list;
  }

  @SuppressWarnings("unchecked")
  private static java.util.Collection<Object> javaCollection(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof java.util.Collection<?> collection)) {
      throw new IllegalStateException("Java mutable collection host value is unavailable");
    }
    return (java.util.Collection<Object>) collection;
  }

  private static Iterable<?> javaIterable(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Iterable<?> iterable)) {
      throw new IllegalStateException("Java iterable host value is unavailable");
    }
    return iterable;
  }

  private static Iterator<?> javaIterator(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Iterator<?> iterator)) {
      throw new IllegalStateException("Java iterator host value is unavailable");
    }
    return iterator;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> javaMap(Object value) {
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof Map<?, ?> map)) {
      throw new IllegalStateException("Java mutable map host value is unavailable");
    }
    return (Map<Object, Object>) map;
  }
}
