package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.ExecutionUpdateResult;
import dev.w0fv1.norm.execution.ExecutionUpdates;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class RuntimeUpdateIntrinsicDispatcher {
  private RuntimeUpdateIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      try {
        var updates =
            context
                .updates()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Source runtime updates are unavailable in this execution"));
        return switch (intrinsic) {
          case RUNTIME_STATUS -> {
            var status = updates.status();
            yield execution
                .values()
                .construct(
                    type,
                    execution,
                    status.revision(),
                    execution.liveBindings().currentRevision(),
                    list(CoreType.STRING, status.pendingLocations()));
          }
          case RUNTIME_LATEST ->
              updates
                  .latest()
                  .map(result -> completion(result, type, execution))
                  .orElse(RuntimeValues.NullValue.INSTANCE);
          case RUNTIME_BEGIN ->
              execution
                  .values()
                  .resource(type, updates.beginBatch(), "runtime update batch", execution);
          case RUNTIME_PUBLISH -> completion(await(updates.request(), execution), type, execution);
          case RUNTIME_COMMIT -> {
            var resource = (RuntimeValues.OpaqueResource) arguments[0];
            yield completion(
                await(resource.resource.value(ExecutionUpdates.Batch.class).commit(), execution),
                type,
                execution);
          }
          case RUNTIME_CLOSE -> {
            ((RuntimeValues.OpaqueResource) arguments[0]).resource.close();
            yield null;
          }
          default ->
              throw new IllegalArgumentException("Not a runtime update intrinsic: " + intrinsic);
        };
      } catch (RuntimeException failure) {
        throw execution.values().javaException(failure, execution, location);
      }
    };
  }

  private static ExecutionUpdateResult await(
      CompletableFuture<ExecutionUpdateResult> future, ExecutionState execution) {
    return execution
        .callbacks()
        .hostCall(
            () -> {
              while (true) {
                if (execution.context().cancellation().getAsBoolean()) {
                  throw new java.util.concurrent.CancellationException(
                      "Runtime publication wait was cancelled");
                }
                try {
                  return future.get(100, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException ignored) {
                } catch (InterruptedException failure) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(
                      "Runtime publication wait was interrupted", failure);
                } catch (java.util.concurrent.ExecutionException failure) {
                  throw new IllegalStateException("Runtime publication failed", failure.getCause());
                }
              }
            });
  }

  private static Object completion(
      ExecutionUpdateResult result, CoreType type, ExecutionState execution) {
    var diagnosticType = ((CoreType.Declared) type).arguments().getFirst();
    var diagnostics =
        result.diagnostics().stream()
            .map(
                value ->
                    execution
                        .values()
                        .construct(
                            diagnosticType,
                            execution,
                            value.code().value(),
                            value.severity().name(),
                            value.message(),
                            value.primarySpan().source().id().uri().toString(),
                            value.primarySpan().startOffset(),
                            value.primarySpan().endOffset()))
            .toList();
    return execution
        .values()
        .construct(
            type,
            execution,
            result.outcome().name(),
            result.status().revision(),
            list(CoreType.STRING, result.status().pendingLocations()),
            list(diagnosticType, diagnostics),
            result.failure().<Object>map(value -> value).orElse(RuntimeValues.NullValue.INSTANCE));
  }

  private static RuntimeValues.ListValue list(CoreType element, List<?> values) {
    return new RuntimeValues.ListValue(
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.List")),
            List.of(element),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL),
        new java.util.ArrayList<>(values));
  }
}
