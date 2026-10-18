package dev.w0fv1.norm.truffle;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class RuntimeCollections {
  private RuntimeCollections() {}

  static final class Storage<T> {
    private final T value;
    private final Function<T, T> copy;
    private final BiConsumer<T, T> replace;

    Storage(T value, Function<T, T> copy, BiConsumer<T, T> replace) {
      this.value = value;
      this.copy = copy;
      this.replace = replace;
    }

    T read() {
      return RuntimeValues.stateView(this);
    }

    T write() {
      return RuntimeValues.stateWrite(this);
    }

    T original() {
      return value;
    }

    T snapshot(T source) {
      return copy.apply(source);
    }

    @SuppressWarnings("unchecked")
    void commit(Object selected) {
      replace.accept(value, (T) selected);
    }
  }

  static <E> List<E> list(Collection<E> values) {
    return new StatefulList<>(new ArrayList<>(values));
  }

  static <K, V> Map<K, V> map() {
    return new StatefulMap<>();
  }

  static <E> Set<E> set() {
    return new StatefulSet<>();
  }

  static <E> Deque<E> deque() {
    return new StatefulDeque<>();
  }

  static <K, V> List<Map.Entry<K, V>> entries(Map<K, V> map) {
    Map<K, V> source = map instanceof StatefulMap<K, V> stateful ? stateful.state.read() : map;
    return source.entrySet().stream()
        .map(AbstractMap.SimpleImmutableEntry::new)
        .map(entry -> (Map.Entry<K, V>) entry)
        .toList();
  }

  static <K, V> void reindex(Map<K, V> map, List<Map.Entry<K, V>> entries, int[] hashes) {
    var rebuilt = new LinkedHashMap<K, V>();
    for (var entry : entries(map)) {
      if (rebuilt.containsKey(entry.getKey()))
        throw new RuntimeValues.StateMigrationFailure(
            "State migration produces duplicate map keys");
      rebuilt.put(entry.getKey(), entry.getValue());
    }
    if (java.util.stream.IntStream.range(0, entries.size())
        .allMatch(index -> entries.get(index).getKey().hashCode() == hashes[index])) return;
    Map<K, V> target = map instanceof StatefulMap<K, V> stateful ? stateful.state.write() : map;
    target.clear();
    target.putAll(rebuilt);
  }

  static <E> void reindex(Set<E> set, List<E> entries, int[] hashes) {
    var rebuilt = new LinkedHashSet<E>();
    for (E entry : set)
      if (!rebuilt.add(entry))
        throw new RuntimeValues.StateMigrationFailure(
            "State migration produces duplicate set keys");
    if (java.util.stream.IntStream.range(0, entries.size())
        .allMatch(index -> entries.get(index).hashCode() == hashes[index])) return;
    Set<E> target = set instanceof StatefulSet<E> stateful ? stateful.state.write() : set;
    target.clear();
    target.addAll(rebuilt);
  }

  private static final class StatefulList<E> extends AbstractList<E> {
    private final Storage<List<E>> state;

    StatefulList(List<E> values) {
      state =
          new Storage<>(
              values,
              ArrayList::new,
              (target, source) -> {
                int shared = Math.min(target.size(), source.size());
                for (int index = 0; index < shared; index++) target.set(index, source.get(index));
                while (target.size() > source.size()) target.remove(target.size() - 1);
                if (source.size() > target.size())
                  target.addAll(source.subList(target.size(), source.size()));
              });
    }

    @Override
    public E get(int index) {
      return state.read().get(index);
    }

    @Override
    public int size() {
      return state.read().size();
    }

    @Override
    public E set(int index, E value) {
      return state.write().set(index, value);
    }

    @Override
    public void add(int index, E value) {
      state.write().add(index, value);
    }

    @Override
    public E remove(int index) {
      return state.write().remove(index);
    }

    @Override
    public void clear() {
      state.write().clear();
    }

    @Override
    public Iterator<E> iterator() {
      List<E> source = state.read();
      Iterator<E> cursor = source.iterator();
      return new Iterator<>() {
        private int index = -1;
        private int removed;
        private boolean removable;

        @Override
        public boolean hasNext() {
          return cursor.hasNext();
        }

        @Override
        public E next() {
          E result = cursor.next();
          index++;
          removable = true;
          return result;
        }

        @Override
        public void remove() {
          if (!removable) throw new IllegalStateException();
          List<E> target = state.write();
          if (target == source) {
            cursor.remove();
            index--;
          } else {
            target.remove(index - removed);
            removed++;
          }
          removable = false;
        }
      };
    }
  }

  private static final class StatefulMap<K, V> extends AbstractMap<K, V> {
    private final Storage<Map<K, V>> state =
        new Storage<>(
            new LinkedHashMap<>(),
            LinkedHashMap::new,
            (target, source) -> {
              target.clear();
              target.putAll(source);
            });

    @Override
    public V get(Object key) {
      return state.read().get(key);
    }

    @Override
    public boolean containsKey(Object key) {
      return state.read().containsKey(key);
    }

    @Override
    public int size() {
      return state.read().size();
    }

    @Override
    public V put(K key, V value) {
      return state.write().put(key, value);
    }

    @Override
    public V remove(Object key) {
      return state.write().remove(key);
    }

    @Override
    public void clear() {
      state.write().clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public int size() {
          return StatefulMap.this.size();
        }

        @Override
        public void clear() {
          StatefulMap.this.clear();
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
          Map<K, V> source = state.read();
          Iterator<Entry<K, V>> cursor = source.entrySet().iterator();
          return new Iterator<>() {
            private K current;
            private boolean removable;

            @Override
            public boolean hasNext() {
              return cursor.hasNext();
            }

            @Override
            public Entry<K, V> next() {
              K key = cursor.next().getKey();
              current = key;
              removable = true;
              return new SimpleEntry<>(key, state.read().get(key)) {
                @Override
                public V getValue() {
                  return StatefulMap.this.get(key);
                }

                @Override
                public V setValue(V value) {
                  return StatefulMap.this.put(key, value);
                }

                @Override
                public int hashCode() {
                  return Objects.hashCode(key) ^ Objects.hashCode(getValue());
                }

                @Override
                public boolean equals(Object other) {
                  return other instanceof Entry<?, ?> entry
                      && Objects.equals(key, entry.getKey())
                      && Objects.equals(getValue(), entry.getValue());
                }
              };
            }

            @Override
            public void remove() {
              if (!removable) throw new IllegalStateException();
              Map<K, V> target = state.write();
              if (target == source) cursor.remove();
              else target.remove(current);
              removable = false;
            }
          };
        }
      };
    }
  }

  private static final class StatefulSet<E> extends AbstractSet<E> {
    private final Storage<Set<E>> state =
        new Storage<>(
            new LinkedHashSet<>(),
            LinkedHashSet::new,
            (target, source) -> {
              target.clear();
              target.addAll(source);
            });

    @Override
    public int size() {
      return state.read().size();
    }

    @Override
    public boolean contains(Object value) {
      return state.read().contains(value);
    }

    @Override
    public boolean add(E value) {
      return state.write().add(value);
    }

    @Override
    public boolean remove(Object value) {
      return state.write().remove(value);
    }

    @Override
    public void clear() {
      state.write().clear();
    }

    @Override
    public Iterator<E> iterator() {
      Set<E> source = state.read();
      Iterator<E> cursor = source.iterator();
      return new Iterator<>() {
        private E current;
        private boolean removable;

        @Override
        public boolean hasNext() {
          return cursor.hasNext();
        }

        @Override
        public E next() {
          current = cursor.next();
          removable = true;
          return current;
        }

        @Override
        public void remove() {
          if (!removable) throw new IllegalStateException();
          Set<E> target = state.write();
          if (target == source) cursor.remove();
          else target.remove(current);
          removable = false;
        }
      };
    }
  }

  private static final class StatefulDeque<E> extends AbstractCollection<E> implements Deque<E> {
    private final Storage<Deque<E>> state =
        new Storage<>(
            new ArrayDeque<>(),
            ArrayDeque::new,
            (target, source) -> {
              target.clear();
              target.addAll(source);
            });

    @Override
    public int size() {
      return state.read().size();
    }

    @Override
    public void addFirst(E value) {
      state.write().addFirst(value);
    }

    @Override
    public void addLast(E value) {
      state.write().addLast(value);
    }

    @Override
    public boolean offerFirst(E value) {
      return state.write().offerFirst(value);
    }

    @Override
    public boolean offerLast(E value) {
      return state.write().offerLast(value);
    }

    @Override
    public E removeFirst() {
      return state.write().removeFirst();
    }

    @Override
    public E removeLast() {
      return state.write().removeLast();
    }

    @Override
    public E pollFirst() {
      return state.write().pollFirst();
    }

    @Override
    public E pollLast() {
      return state.write().pollLast();
    }

    @Override
    public E getFirst() {
      return state.read().getFirst();
    }

    @Override
    public E getLast() {
      return state.read().getLast();
    }

    @Override
    public E peekFirst() {
      return state.read().peekFirst();
    }

    @Override
    public E peekLast() {
      return state.read().peekLast();
    }

    @Override
    public boolean removeFirstOccurrence(Object value) {
      return state.write().removeFirstOccurrence(value);
    }

    @Override
    public boolean removeLastOccurrence(Object value) {
      return state.write().removeLastOccurrence(value);
    }

    @Override
    public boolean add(E value) {
      return state.write().add(value);
    }

    @Override
    public boolean offer(E value) {
      return state.write().offer(value);
    }

    @Override
    public E remove() {
      return state.write().remove();
    }

    @Override
    public E poll() {
      return state.write().poll();
    }

    @Override
    public E element() {
      return state.read().element();
    }

    @Override
    public E peek() {
      return state.read().peek();
    }

    @Override
    public void push(E value) {
      state.write().push(value);
    }

    @Override
    public E pop() {
      return state.write().pop();
    }

    @Override
    public boolean remove(Object value) {
      return state.write().remove(value);
    }

    @Override
    public void clear() {
      state.write().clear();
    }

    @Override
    public Iterator<E> iterator() {
      return cursor(state.read(), false);
    }

    @Override
    public Iterator<E> descendingIterator() {
      return cursor(state.read(), true);
    }

    private Iterator<E> cursor(Deque<E> source, boolean descending) {
      Iterator<E> iterator = descending ? source.descendingIterator() : source.iterator();
      return new Iterator<>() {
        private int index = -1;
        private int removed;
        private boolean removable;

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public E next() {
          E result = iterator.next();
          index++;
          removable = true;
          return result;
        }

        @Override
        public void remove() {
          if (!removable) throw new IllegalStateException();
          Deque<E> target = state.write();
          if (target == source) {
            iterator.remove();
            index--;
          } else {
            Iterator<E> writable = descending ? target.descendingIterator() : target.iterator();
            for (int offset = 0; offset <= index - removed; offset++) writable.next();
            writable.remove();
            removed++;
          }
          removable = false;
        }
      };
    }
  }
}
