package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class FieldObservations {
  private final List<Subscription> subscriptions = new ArrayList<>();
  private final RuntimeValues.ObjectValue target;
  private final java.util.Map<Integer, List<Runnable>> bindings = new java.util.HashMap<>();

  FieldObservations(RuntimeValues.ObjectValue target) {
    this.target = target;
  }

  @TruffleBoundary
  void bind(int field) {
    var previous = bindings.remove(field);
    if (previous != null) previous.forEach(Runnable::run);
    if (subscriptions.stream().noneMatch(subscription -> subscription.accepts(field))) return;
    var releases = new ArrayList<Runnable>();
    ValueObservations.subscribeTree(
        target.fields[field],
        () -> {
          var value = RuntimeValues.copy(target.fields[field]);
          return () -> target.fieldChanged(field, value);
        },
        releases);
    if (!releases.isEmpty()) bindings.put(field, releases);
  }

  @TruffleBoundary
  AutoCloseable subscribe(
      java.util.OptionalInt selected, IntConsumer read, Consumer<Change> changed) {
    var subscription = new Subscription(selected, read, changed);
    subscriptions.add(subscription);
    if (selected.isPresent()) bind(selected.getAsInt());
    else for (int field = 0; field < target.fields.length; field++) bind(field);
    return subscription;
  }

  @TruffleBoundary
  void read(int field) {
    for (var subscription : List.copyOf(subscriptions)) {
      if (subscription.accepts(field)) subscription.read.accept(field);
    }
  }

  @TruffleBoundary
  void changed(int field, Object previous, Object current) {
    if (subscriptions.stream().noneMatch(subscription -> subscription.accepts(field))) return;
    var change = new Change(field, RuntimeValues.copy(previous), RuntimeValues.copy(current));
    RuntimeException failure = null;
    for (var subscription : List.copyOf(subscriptions)) {
      if (!subscription.accepts(field)) continue;
      try {
        subscription.changed.accept(change);
      } catch (RuntimeException exception) {
        if (failure == null) failure = exception;
        else if (failure != exception) failure.addSuppressed(exception);
      }
    }
    if (failure != null) throw failure;
  }

  private final class Subscription implements AutoCloseable {
    private final java.util.OptionalInt selected;
    private final IntConsumer read;
    private final Consumer<Change> changed;
    private boolean closed;

    Subscription(java.util.OptionalInt selected, IntConsumer read, Consumer<Change> changed) {
      this.selected = selected;
      this.read = read;
      this.changed = changed;
    }

    boolean accepts(int field) {
      return !closed && (selected.isEmpty() || selected.getAsInt() == field);
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      subscriptions.remove(this);
      for (var field : List.copyOf(bindings.keySet())) bind(field);
    }
  }

  record Change(int field, Object previous, Object current) {}
}
