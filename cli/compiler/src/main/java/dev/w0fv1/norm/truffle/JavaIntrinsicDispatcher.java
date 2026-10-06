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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class JavaIntrinsicDispatcher {
  private JavaIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
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
}
