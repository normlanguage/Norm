package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.semantic.SemanticType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class RuntimeValues {
  private static final Pattern GRAPHEME = Pattern.compile("\\X");

  private RuntimeValues() {}

  static Object copy(Object value) {
    return switch (value) {
      case ArrayValue array -> new ArrayValue(array.type, copyList(array.values));
      case ListValue list -> new ListValue(list.type, copyList(list.values));
      case MapValue map -> {
        MapValue result = new MapValue(map.type);
        map.values.forEach((key, item) -> result.values.put(copy(key), copy(item)));
        yield result;
      }
      case SetValue set -> {
        SetValue result = new SetValue(set.type);
        set.values.forEach(item -> result.values.add(copy(item)));
        yield result;
      }
      case StackValue stack -> {
        StackValue result = new StackValue(stack.type);
        List<Object> items = new ArrayList<>(stack.values);
        for (int index = items.size() - 1; index >= 0; index--) {
          result.values.push(copy(items.get(index)));
        }
        yield result;
      }
      case QueueValue queue -> {
        QueueValue result = new QueueValue(queue.type);
        queue.values.forEach(item -> result.values.addLast(copy(item)));
        yield result;
      }
      case DequeValue deque -> {
        DequeValue result = new DequeValue(deque.type);
        deque.values.forEach(item -> result.values.addLast(copy(item)));
        yield result;
      }
      case PairValue pair -> new PairValue(pair.type, copy(pair.first), copy(pair.second));
      case BuilderValue builder -> new BuilderValue(builder.value.toString());
      case ObjectValue object -> object;
      case null, default -> value;
    };
  }

  static ObjectValue copyObject(ObjectValue object) {
    ObjectValue result = new ObjectValue(object.classInfo, object.type);
    for (int index = 0; index < object.fields.length; index++) {
      result.fields[index] = copy(object.fields[index]);
    }
    return result;
  }

  static boolean equal(Object left, Object right) {
    if (left == right) return true;
    if (left == null || right == null || left.getClass() != right.getClass()) return false;
    return switch (left) {
      case ArrayValue value -> equalLists(value.values, ((ArrayValue) right).values);
      case ListValue value -> equalLists(value.values, ((ListValue) right).values);
      case MapValue value -> equalMaps(value, (MapValue) right);
      case SetValue value -> equalSets(value, (SetValue) right);
      case StackValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((StackValue) right).values));
      case QueueValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((QueueValue) right).values));
      case DequeValue value ->
          equalLists(new ArrayList<>(value.values), new ArrayList<>(((DequeValue) right).values));
      case PairValue value ->
          equal(value.first, ((PairValue) right).first)
              && equal(value.second, ((PairValue) right).second);
      case BuilderValue value -> value.value.toString().contentEquals(((BuilderValue) right).value);
      case RangeValue value ->
          value.start == ((RangeValue) right).start && value.end == ((RangeValue) right).end;
      case ObjectValue ignored -> false;
      default -> Objects.equals(left, right);
    };
  }

  static void mapPut(MapValue map, Object key, Object value) {
    Object existing = findEqual(map.values.keySet(), key);
    map.values.put(existing == null ? copy(key) : existing, copy(value));
  }

  static Object mapGet(MapValue map, Object key) {
    Object existing = findEqual(map.values.keySet(), key);
    if (existing == null) throw new IllegalStateException("map key lookup invariant violated");
    return map.values.get(existing);
  }

  static boolean mapContains(MapValue map, Object key) {
    return findEqual(map.values.keySet(), key) != null;
  }

  static boolean mapRemove(MapValue map, Object key) {
    Object existing = findEqual(map.values.keySet(), key);
    return existing != null && map.values.remove(existing) != null;
  }

  static boolean setAdd(SetValue set, Object value) {
    if (findEqual(set.values, value) != null) return false;
    return set.values.add(copy(value));
  }

  static boolean setContains(SetValue set, Object value) {
    return findEqual(set.values, value) != null;
  }

  static boolean setRemove(SetValue set, Object value) {
    Object existing = findEqual(set.values, value);
    return existing != null && set.values.remove(existing);
  }

  static long size(Object value) {
    return switch (value) {
      case String string -> string.length();
      case ArrayValue array -> array.values.size();
      case ListValue list -> list.values.size();
      case MapValue map -> map.values.size();
      case SetValue set -> set.values.size();
      case StackValue stack -> stack.values.size();
      case QueueValue queue -> queue.values.size();
      case DequeValue deque -> deque.values.size();
      case RangeValue range -> Math.max(0, range.end - range.start);
      case BuilderValue builder -> builder.value.length();
      default -> throw new IllegalStateException("value has no size");
    };
  }

  static long byteSize(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  static long codePointSize(String value) {
    return value.codePointCount(0, value.length());
  }

  static long graphemeSize(String value) {
    return GRAPHEME.matcher(value).results().count();
  }

  static String stringify(Object value) {
    return value == null ? "Void" : value.toString();
  }

  private static List<Object> copyList(List<Object> values) {
    List<Object> result = new ArrayList<>(values.size());
    values.forEach(value -> result.add(copy(value)));
    return result;
  }

  private static boolean equalLists(List<Object> left, List<Object> right) {
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++) {
      if (!equal(left.get(index), right.get(index))) return false;
    }
    return true;
  }

  private static boolean equalMaps(MapValue left, MapValue right) {
    if (left.values.size() != right.values.size()) return false;
    for (Map.Entry<Object, Object> entry : left.values.entrySet()) {
      Object key = findEqual(right.values.keySet(), entry.getKey());
      if (key == null || !equal(entry.getValue(), right.values.get(key))) return false;
    }
    return true;
  }

  private static boolean equalSets(SetValue left, SetValue right) {
    if (left.values.size() != right.values.size()) return false;
    for (Object value : left.values) {
      if (findEqual(right.values, value) == null) return false;
    }
    return true;
  }

  private static Object findEqual(Iterable<Object> values, Object expected) {
    for (Object value : values) {
      if (equal(value, expected)) return value;
    }
    return null;
  }

  static final class ArrayValue {
    final SemanticType type;
    final List<Object> values;

    ArrayValue(SemanticType type, List<Object> values) {
      this.type = type;
      this.values = values;
    }
  }

  static final class ListValue {
    final SemanticType type;
    final List<Object> values;

    ListValue(SemanticType type) {
      this(type, new ArrayList<>());
    }

    ListValue(SemanticType type, List<Object> values) {
      this.type = type;
      this.values = values;
    }
  }

  static final class MapValue {
    final SemanticType type;
    final Map<Object, Object> values = new LinkedHashMap<>();

    MapValue(SemanticType type) {
      this.type = type;
    }
  }

  static final class SetValue {
    final SemanticType type;
    final java.util.Set<Object> values = new LinkedHashSet<>();

    SetValue(SemanticType type) {
      this.type = type;
    }
  }

  static final class StackValue {
    final SemanticType type;
    final Deque<Object> values = new ArrayDeque<>();

    StackValue(SemanticType type) {
      this.type = type;
    }
  }

  static final class QueueValue {
    final SemanticType type;
    final Deque<Object> values = new ArrayDeque<>();

    QueueValue(SemanticType type) {
      this.type = type;
    }
  }

  static final class DequeValue {
    final SemanticType type;
    final Deque<Object> values = new ArrayDeque<>();

    DequeValue(SemanticType type) {
      this.type = type;
    }
  }

  static final class BuilderValue {
    final StringBuilder value;

    BuilderValue() {
      this("");
    }

    BuilderValue(String value) {
      this.value = new StringBuilder(value);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }

  static final class PairValue {
    final SemanticType type;
    Object first;
    Object second;

    PairValue(SemanticType type, Object first, Object second) {
      this.type = type;
      this.first = first;
      this.second = second;
    }
  }

  record EnumValue(String enumName, String member) {
    @Override
    public String toString() {
      return enumName + "." + member;
    }
  }

  static final class RangeValue {
    final long start;
    final long end;

    RangeValue(long start, long end) {
      this.start = start;
      this.end = end;
    }

    Iterator<Object> iterator() {
      return new Iterator<>() {
        private long current = start;

        @Override
        public boolean hasNext() {
          return current < end;
        }

        @Override
        public Object next() {
          return current++;
        }
      };
    }
  }

  record ClassInfo(String name, int fieldCount) {}

  static final class ObjectValue {
    final ClassInfo classInfo;
    final SemanticType type;
    final Object[] fields;

    ObjectValue(ClassInfo classInfo, SemanticType type) {
      this.classInfo = classInfo;
      this.type = type;
      fields = new Object[classInfo.fieldCount()];
    }
  }
}
