package dev.w0fv1.norm.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AnalysisJournal {
  private final List<Runnable> changes = new ArrayList<>();
  private Checkpoint active;

  Checkpoint checkpoint() {
    active = new Checkpoint(active, changes.size());
    return active;
  }

  void restore(Checkpoint checkpoint) {
    if (active != checkpoint)
      throw new IllegalStateException("analysis checkpoints must restore in nesting order");
    for (int index = changes.size() - 1; index >= checkpoint.mark; index--)
      changes.removeLast().run();
    active = checkpoint.parent;
  }

  <K, V> void put(Map<K, V> target, K key, V value) {
    if (active != null) {
      boolean present = target.containsKey(key);
      V previous = target.get(key);
      changes.add(
          () -> {
            if (present) target.put(key, previous);
            else target.remove(key);
          });
    }
    target.put(key, value);
  }

  <K, V> void putIfAbsent(Map<K, V> target, K key, V value) {
    if (target.get(key) == null) put(target, key, value);
  }

  <K, V> void putAll(Map<K, V> target, Map<K, V> values) {
    values.forEach((key, value) -> put(target, key, value));
  }

  <T> void add(Set<T> target, T value) {
    if (target.add(value) && active != null) changes.add(() -> target.remove(value));
  }

  <T> void add(List<T> target, T value) {
    target.add(value);
    if (active != null) changes.add(target::removeLast);
  }

  static final class Checkpoint {
    private final Checkpoint parent;
    private final int mark;

    private Checkpoint(Checkpoint parent, int mark) {
      this.parent = parent;
      this.mark = mark;
    }
  }
}
