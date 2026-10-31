package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Objects;
import java.util.function.BooleanSupplier;

record ExecutionState(
    ExecutionContext context,
    AnnotationRuntime.Execution annotationExecution,
    GuestValueFactory values,
    ResourceScope resources,
    GuestCallbackScheduler callbacks,
    ExecutionContexts contexts,
    ApplicationLogSession logs,
    LiveBindings liveBindings) {
  ExecutionState {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(annotationExecution, "annotationExecution");
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(callbacks, "callbacks");
    Objects.requireNonNull(contexts, "contexts");
  }

  AnnotationRuntime runtime() {
    return liveBindings == null ? annotationExecution.runtime() : liveBindings.runtime();
  }

  @Override
  public GuestValueFactory values() {
    return liveBindings == null ? values : runtime().values();
  }

  ClassLoader applicationClassLoader() {
    var selected = liveBindings == null ? context : liveBindings.context(context);
    return ((dev.w0fv1.norm.execution.JavaApplicationRuntime) selected.jarBindingRuntime())
        .applicationClassLoader();
  }

  void runCallbacksUntil(BooleanSupplier completed, Node location) {
    callbacks.runUntil(
        () -> {
          if (context.cancellation().getAsBoolean()) {
            throw new NormGuestException(
                RuntimeErrorCode.CANCELLED, "execution cancelled", location);
          }
          return completed.getAsBoolean();
        });
  }

  void close(Throwable failure) {
    try {
      resources.close();
    } catch (ResourceCloseException closeFailure) {
      if (failure == null) throw closeFailure;
      failure.addSuppressed(closeFailure);
    } finally {
      callbacks.close();
    }
  }
}
