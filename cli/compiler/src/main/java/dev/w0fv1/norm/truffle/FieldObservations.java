package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class FieldObservations {
  private final List<Subscription> subscriptions = new ArrayList<>();
  private final RuntimeValues.ObjectValue target;
  private final java.util.Map<Site, List<Runnable>> bindings = new java.util.HashMap<>();

  FieldObservations(RuntimeValues.ObjectValue target) {
    this.target = target;
  }

  @TruffleBoundary
  void bind(RuntimeValues.FieldKey field, RuntimeValues.AggregateInfo view) {
    var site = new Site(field, view);
    var previous = bindings.remove(site);
    if (previous != null) previous.forEach(Runnable::run);
    if (subscriptions.stream().noneMatch(subscription -> subscription.accepts(site))) return;
    var releases = new ArrayList<Runnable>();
    ValueObservations.subscribeTree(
        target.rawField(field, view),
        () -> {
          var value = RuntimeValues.copy(target.rawField(field, view));
          return () -> target.fieldChanged(field, value, view);
        },
        releases);
    if (!releases.isEmpty()) bindings.put(site, releases);
  }

  @TruffleBoundary
  AutoCloseable subscribe(
      Optional<RuntimeValues.FieldKey> selected,
      RuntimeValues.AggregateInfo view,
      IntConsumer read,
      Consumer<Change> changed) {
    var subscription = new Subscription(selected, view, read, changed);
    subscriptions.add(subscription);
    if (selected.isPresent()) bind(selected.orElseThrow(), view);
    else view.fields().forEach(field -> bind(field.key(), view));
    return subscription;
  }

  @TruffleBoundary
  void read(RuntimeValues.FieldKey field, RuntimeValues.AggregateInfo view) {
    var site = new Site(field, view);
    for (var subscription : List.copyOf(subscriptions)) {
      if (subscription.accepts(site)) subscription.read.accept(subscription.index(field));
    }
  }

  @TruffleBoundary
  void changed(
      RuntimeValues.FieldKey field,
      RuntimeValues.AggregateInfo view,
      Object previous,
      Object current) {
    var site = new Site(field, view);
    Object before = RuntimeValues.copy(previous);
    Object after = RuntimeValues.copy(current);
    RuntimeException failure = null;
    for (var subscription : List.copyOf(subscriptions)) {
      if (!subscription.accepts(site)) continue;
      try {
        subscription.changed.accept(
            new Change(
                subscription.index(field), RuntimeValues.copy(before), RuntimeValues.copy(after)));
      } catch (RuntimeException exception) {
        if (failure == null) failure = exception;
        else if (failure != exception) failure.addSuppressed(exception);
      }
    }
    if (failure != null) throw failure;
  }

  private final class Subscription implements AutoCloseable {
    private final Optional<RuntimeValues.FieldKey> selected;
    private final RuntimeValues.AggregateInfo view;
    private final IntConsumer read;
    private final Consumer<Change> changed;
    private boolean closed;

    Subscription(
        Optional<RuntimeValues.FieldKey> selected,
        RuntimeValues.AggregateInfo view,
        IntConsumer read,
        Consumer<Change> changed) {
      this.selected = selected;
      this.view = view;
      this.read = read;
      this.changed = changed;
    }

    int index(RuntimeValues.FieldKey field) {
      return view.fields().stream()
          .filter(plan -> plan.key().equals(field))
          .findFirst()
          .orElseThrow()
          .index();
    }

    boolean accepts(Site site) {
      return !closed
          && (selected.isEmpty() || selected.orElseThrow().equals(site.field()))
          && target.state().sameLocation(view, site.view(), target.type, site.field());
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      subscriptions.remove(this);
      for (var site : List.copyOf(bindings.keySet())) bind(site.field(), site.view());
    }
  }

  private record Site(RuntimeValues.FieldKey field, RuntimeValues.AggregateInfo view) {}

  record Change(int field, Object previous, Object current) {}
}
