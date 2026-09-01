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
    GuestCallbackScheduler callbacks) {
  ExecutionState {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(annotationExecution, "annotationExecution");
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(resources, "resources");
    Objects.requireNonNull(callbacks, "callbacks");
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
    callbacks.close();
    try {
      resources.close();
    } catch (ResourceCloseException closeFailure) {
      if (failure == null) throw closeFailure;
      failure.addSuppressed(closeFailure);
    }
  }
}
