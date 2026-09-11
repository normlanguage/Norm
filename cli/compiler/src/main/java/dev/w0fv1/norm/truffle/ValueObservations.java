package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class ValueObservations {
  private final List<Supplier<Runnable>> observers = new ArrayList<>();

  static IntrinsicOperation mutation(IntrinsicOperation operation) {
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      var value = (RuntimeValues.ContainerValue) receiver;
      var notifications =
          value.observations == null ? List.<Runnable>of() : value.observations.begin();
      Throwable primary = null;
      try {
        return operation.execute(
            receiver, arguments, type, context, location, annotations, execution);
      } catch (RuntimeException | Error failure) {
        primary = failure;
        throw failure;
      } finally {
        complete(notifications, primary);
      }
    };
  }

  Runnable subscribe(Supplier<Runnable> observer) {
    observers.add(observer);
    return () -> observers.remove(observer);
  }

  @TruffleBoundary
  List<Runnable> begin() {
    return List.copyOf(observers).stream().map(Supplier::get).toList();
  }

  @TruffleBoundary
  static void complete(List<Runnable> notifications, Throwable primary) {
    RuntimeException failure = null;
    for (var notification : notifications) {
      try {
        notification.run();
      } catch (RuntimeException error) {
        if (primary != null) {
          if (primary != error) primary.addSuppressed(error);
        } else if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      }
    }
    if (failure != null) throw failure;
  }

  static void subscribeTree(Object value, Supplier<Runnable> observer, List<Runnable> releases) {
    if (!(value instanceof RuntimeValues.ContainerValue container)) return;
    if (container.observations == null) container.observations = new ValueObservations();
    releases.add(container.observations.subscribe(observer));
    Iterable<?> children =
        switch (container) {
          case RuntimeValues.ListValue list -> list.values;
          case RuntimeValues.ArrayValue array -> array.values;
          case RuntimeValues.MapValue map -> map.values.values();
          case RuntimeValues.StackValue stack -> stack.values;
          case RuntimeValues.QueueValue queue -> queue.values;
          case RuntimeValues.DequeValue deque -> deque.values;
          case RuntimeValues.PairValue pair -> java.util.Arrays.asList(pair.first, pair.second);
          default -> List.of();
        };
    for (var child : children) subscribeTree(child, observer, releases);
  }
}
