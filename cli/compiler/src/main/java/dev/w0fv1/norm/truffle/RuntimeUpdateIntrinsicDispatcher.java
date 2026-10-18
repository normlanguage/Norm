package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.ExecutionUpdateEvent;
import dev.w0fv1.norm.execution.ExecutionUpdateResult;
import dev.w0fv1.norm.execution.ExecutionUpdateSubscription;
import dev.w0fv1.norm.execution.ExecutionUpdates;
import java.time.Duration;
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
          case RUNTIME_SUBSCRIBE ->
              execution
                  .values()
                  .resource(type, updates.subscribe(), "runtime update subscription", execution);
          case RUNTIME_NEXT -> {
            var resource = (RuntimeValues.OpaqueResource) arguments[0];
            var event =
                await(resource.resource.value(ExecutionUpdateSubscription.class), execution);
            yield event
                .map(value -> event(value, type, execution))
                .orElse(RuntimeValues.NullValue.INSTANCE);
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

  private static java.util.Optional<ExecutionUpdateEvent> await(
      ExecutionUpdateSubscription subscription, ExecutionState execution) {
    return execution
        .callbacks()
        .hostCall(
            () -> {
              while (true) {
                if (execution.context().cancellation().getAsBoolean()) {
                  throw new java.util.concurrent.CancellationException(
                      "Runtime update event wait was cancelled");
                }
                try {
                  var event = subscription.next(Duration.ofMillis(100));
                  if (event.isPresent()) return event;
                } catch (InterruptedException failure) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(
                      "Runtime update event wait was interrupted", failure);
                }
              }
            });
  }

  private static Object event(ExecutionUpdateEvent event, CoreType type, ExecutionState execution) {
    var eventType = nonNullable(type);
    var changes =
        event
            .result()
            .map(ExecutionUpdateResult::changes)
            .map(
                value ->
                    value.declarations().stream()
                        .map(RuntimeUpdateIntrinsicDispatcher::summary)
                        .toList())
            .orElse(List.of());
    return execution
        .values()
        .construct(
            eventType,
            execution,
            event.sequence(),
            eventKind(event.kind()),
            event.status().revision(),
            list(CoreType.STRING, event.status().pendingLocations()),
            list(CoreType.STRING, changes));
  }

  private static String summary(dev.w0fv1.norm.execution.RuntimeDeclarationChange change) {
    return change.kind().name()
        + " "
        + change.declarationKind().name()
        + " "
        + change.identity()
        + " : "
        + change.before().map(Object::toString).orElse("<none>")
        + " -> "
        + change.after().map(Object::toString).orElse("<none>");
  }

  private static String eventKind(dev.w0fv1.norm.execution.ExecutionUpdateEventKind kind) {
    return switch (kind) {
      case REQUESTED -> "Requested";
      case PUBLISHED -> "Published";
      case UNCHANGED -> "Unchanged";
      case REJECTED -> "Rejected";
      case FAILED -> "Failed";
      case ADOPTION_PENDING -> "AdoptionPending";
      case ADOPTED -> "Adopted";
    };
  }

  private static CoreType nonNullable(CoreType type) {
    if (type instanceof CoreType.Declared declared
        && declared.nullability() == CoreNullability.NULLABLE)
      return new CoreType.Declared(
          declared.constructor(),
          declared.arguments(),
          declared.category(),
          CoreNullability.NON_NULL);
    return type;
  }

  private static Object completion(
      ExecutionUpdateResult result, CoreType type, ExecutionState execution) {
    var diagnosticType = ((CoreType.Declared) type).arguments().getFirst();
    var changeType = ((CoreType.Declared) type).arguments().get(1);
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
    var changes =
        result.changes().declarations().stream()
            .map(
                value ->
                    execution
                        .values()
                        .construct(
                            changeType,
                            execution,
                            value.kind().name(),
                            value.declarationKind().name(),
                            value.identity(),
                            value
                                .before()
                                .<Object>map(Object::toString)
                                .orElse(RuntimeValues.NullValue.INSTANCE),
                            value
                                .after()
                                .<Object>map(Object::toString)
                                .orElse(RuntimeValues.NullValue.INSTANCE),
                            value.source(),
                            value.startOffset(),
                            value.endOffset()))
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
            list(changeType, changes),
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
