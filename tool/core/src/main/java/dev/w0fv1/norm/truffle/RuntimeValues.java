package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
      case EnumValue enumValue ->
          new EnumValue(
              enumValue.definition,
              enumValue.type,
              enumValue.enumName,
              enumValue.variantKey,
              copyList(enumValue.payload));
      case BuilderValue builder -> new BuilderValue(builder.value.toString());
      case NativeIteratorValue iterator -> iterator;
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
      case Integer value -> value.intValue() == (Integer) right;
      case Long value -> value.longValue() == (Long) right;
      case Float value -> value.floatValue() == (Float) right;
      case Double value -> value.doubleValue() == (Double) right;
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
      case EnumValue value -> value.sameValue((EnumValue) right);
      case BuilderValue value -> value.value.toString().contentEquals(((BuilderValue) right).value);
      case RangeValue value ->
          value.start == ((RangeValue) right).start
              && value.end == ((RangeValue) right).end
              && value.step == ((RangeValue) right).step;
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

  static Object mapGetOrNull(MapValue map, Object key) {
    Object existing = findEqual(map.values.keySet(), key);
    return existing == null ? NullValue.INSTANCE : map.values.get(existing);
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

  static int size(Object value) {
    return switch (value) {
      case String string -> string.length();
      case ArrayValue array -> array.values.size();
      case ListValue list -> list.values.size();
      case MapValue map -> map.values.size();
      case SetValue set -> set.values.size();
      case StackValue stack -> stack.values.size();
      case QueueValue queue -> queue.values.size();
      case DequeValue deque -> deque.values.size();
      case RangeValue range -> range.size();
      case BuilderValue builder -> builder.value.length();
      default -> throw new IllegalStateException("value has no size");
    };
  }

  static int byteSize(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  static int codePointSize(String value) {
    return value.codePointCount(0, value.length());
  }

  static int graphemeSize(String value) {
    return Math.toIntExact(GRAPHEME.matcher(value).results().count());
  }

  static ArrayValue codePoints(String value) {
    CoreType type = arrayType(CoreType.CODE_POINT);
    List<Object> values =
        value.codePoints().mapToObj(CodePointValue::new).map(item -> (Object) item).toList();
    return new ArrayValue(type, new ArrayList<>(values));
  }

  static ArrayValue graphemes(String value) {
    CoreType type = arrayType(CoreType.STRING);
    List<Object> values =
        GRAPHEME.matcher(value).results().map(result -> (Object) result.group()).toList();
    return new ArrayValue(type, new ArrayList<>(values));
  }

  static String sliceCodePoints(String value, int start, int end, Node location) {
    int size = value.codePointCount(0, value.length());
    int from = checkedCodePointIndex(start, size, location);
    int to = checkedCodePointIndex(end, size, location);
    if (from > to) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS, "code point slice start exceeds end", location);
    }
    int fromOffset = value.offsetByCodePoints(0, from);
    int toOffset = value.offsetByCodePoints(0, to);
    return value.substring(fromOffset, toOffset);
  }

  static ArrayValue split(String value, String separator, Node location) {
    if (separator.isEmpty()) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "string separator must not be empty", location);
    }
    CoreType type = arrayType(CoreType.STRING);
    List<Object> parts = new ArrayList<>();
    int start = 0;
    int index;
    while ((index = value.indexOf(separator, start)) >= 0) {
      parts.add(value.substring(start, index));
      start = index + separator.length();
    }
    parts.add(value.substring(start));
    return new ArrayValue(type, parts);
  }

  static String sliceGraphemes(String value, int start, int end, Node location) {
    List<java.util.regex.MatchResult> matches = GRAPHEME.matcher(value).results().toList();
    int from = checkedTextIndex(start, matches.size(), "grapheme", location);
    int to = checkedTextIndex(end, matches.size(), "grapheme", location);
    if (from > to) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS, "grapheme slice start exceeds end", location);
    }
    int fromOffset = from == matches.size() ? value.length() : matches.get(from).start();
    int toOffset = to == matches.size() ? value.length() : matches.get(to).start();
    return value.substring(fromOffset, toOffset);
  }

  static String replaceFirst(String value, String target, String replacement, Node location) {
    requireReplaceTarget(target, location);
    int index = value.indexOf(target);
    if (index < 0) return value;
    return value.substring(0, index) + replacement + value.substring(index + target.length());
  }

  static String replace(String value, String target, String replacement, Node location) {
    requireReplaceTarget(target, location);
    return value.replace(target, replacement);
  }

  static String trim(String value) {
    return trimEnd(trimStart(value));
  }

  static String trimStart(String value) {
    int offset = 0;
    while (offset < value.length()) {
      int codePoint = value.codePointAt(offset);
      if (!isWhitespace(codePoint)) break;
      offset += Character.charCount(codePoint);
    }
    return value.substring(offset);
  }

  static String trimEnd(String value) {
    int offset = value.length();
    while (offset > 0) {
      int codePoint = value.codePointBefore(offset);
      if (!isWhitespace(codePoint)) break;
      offset -= Character.charCount(codePoint);
    }
    return value.substring(0, offset);
  }

  static boolean isWhitespace(int value) {
    return Character.isWhitespace(value) || Character.isSpaceChar(value);
  }

  static String toLowercase(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  static String toUppercase(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  static boolean equalsIgnoreCaseAscii(String left, String right) {
    if (left.length() != right.length()) return false;
    for (int index = 0; index < left.length(); index++) {
      char leftCharacter = asciiLowercase(left.charAt(index));
      char rightCharacter = asciiLowercase(right.charAt(index));
      if (leftCharacter != rightCharacter) return false;
    }
    return true;
  }

  static int compareCodePoints(String left, String right) {
    var leftIterator = left.codePoints().iterator();
    var rightIterator = right.codePoints().iterator();
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      int leftValue = leftIterator.nextInt();
      int rightValue = rightIterator.nextInt();
      if (leftValue < rightValue) return -1;
      if (leftValue > rightValue) return 1;
    }
    if (leftIterator.hasNext()) return 1;
    if (rightIterator.hasNext()) return -1;
    return 0;
  }

  static String normalizeNfc(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC);
  }

  static String normalizeNfd(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD);
  }

  static String normalizeNfkc(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC);
  }

  static String normalizeNfkd(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKD);
  }

  static boolean isNormalizedNfc(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFC);
  }

  static boolean isNormalizedNfd(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFD);
  }

  static boolean isNormalizedNfkc(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFKC);
  }

  static boolean isNormalizedNfkd(String value) {
    return Normalizer.isNormalized(value, Normalizer.Form.NFKD);
  }

  private static char asciiLowercase(char value) {
    return value >= 'A' && value <= 'Z' ? (char) (value + ('a' - 'A')) : value;
  }

  private static void requireReplaceTarget(String target, Node location) {
    if (target.isEmpty()) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "replace target must not be empty", location);
    }
  }

  private static int checkedCodePointIndex(int index, int size, Node location) {
    return checkedTextIndex(index, size, "code point", location);
  }

  private static int checkedTextIndex(int index, int size, String unit, Node location) {
    if (index < 0 || index > size) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS,
          unit + " index " + index + " is outside 0.." + size,
          location);
    }
    return index;
  }

  static String stringify(Object value) {
    return value == null ? "Void" : value.toString();
  }

  static BuiltinTypeId builtinType(Object value) {
    CoreType type =
        switch (value) {
          case Integer ignored -> CoreType.INTEGER;
          case Long ignored -> CoreType.LONG;
          case Float ignored -> CoreType.FLOAT;
          case Double ignored -> CoreType.DOUBLE;
          case Boolean ignored -> CoreType.BOOLEAN;
          case String ignored -> CoreType.STRING;
          case CodePointValue ignored -> CoreType.CODE_POINT;
          case ArrayValue item -> item.type;
          case ListValue item -> item.type;
          case MapValue item -> item.type;
          case SetValue item -> item.type;
          case StackValue item -> item.type;
          case QueueValue item -> item.type;
          case DequeValue item -> item.type;
          case PairValue item -> item.type;
          case RangeValue ignored ->
              new CoreType.Declared(
                  new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Range")),
                  List.of(),
                  CoreValueCategory.VALUE,
                  CoreNullability.NON_NULL);
          case BuilderValue ignored ->
              new CoreType.Declared(
                  new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.StringBuilder")),
                  List.of(),
                  CoreValueCategory.IDENTITY,
                  CoreNullability.NON_NULL);
          case NativeIteratorValue item -> item.type;
          default -> throw new IllegalStateException("interface receiver has no builtin type");
        };
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)) {
      throw new IllegalStateException("interface receiver has no builtin type");
    }
    return builtin.id();
  }

  enum NullValue {
    INSTANCE;

    @Override
    public String toString() {
      return "null";
    }
  }

  record CodePointValue(int value) {
    CodePointValue {
      if (!Character.isValidCodePoint(value)
          || value >= Character.MIN_SURROGATE && value <= Character.MAX_SURROGATE) {
        throw new IllegalArgumentException("invalid Unicode code point");
      }
    }

    @Override
    public String toString() {
      return new String(Character.toChars(value));
    }
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

  private static CoreType arrayType(CoreType element) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Array")),
        List.of(element),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  static final class ArrayValue {
    final CoreType type;
    final List<Object> values;

    ArrayValue(CoreType type, List<Object> values) {
      this.type = type;
      this.values = values;
    }
  }

  static final class ListValue {
    final CoreType type;
    final List<Object> values;

    ListValue(CoreType type) {
      this(type, new ArrayList<>());
    }

    ListValue(CoreType type, List<Object> values) {
      this.type = type;
      this.values = values;
    }
  }

  static final class MapValue {
    final CoreType type;
    final Map<Object, Object> values = new LinkedHashMap<>();

    MapValue(CoreType type) {
      this.type = type;
    }
  }

  static final class SetValue {
    final CoreType type;
    final java.util.Set<Object> values = new LinkedHashSet<>();

    SetValue(CoreType type) {
      this.type = type;
    }
  }

  static final class StackValue {
    final CoreType type;
    final Deque<Object> values = new ArrayDeque<>();

    StackValue(CoreType type) {
      this.type = type;
    }
  }

  static final class QueueValue {
    final CoreType type;
    final Deque<Object> values = new ArrayDeque<>();

    QueueValue(CoreType type) {
      this.type = type;
    }
  }

  static final class DequeValue {
    final CoreType type;
    final Deque<Object> values = new ArrayDeque<>();

    DequeValue(CoreType type) {
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
    final CoreType type;
    Object first;
    Object second;

    PairValue(CoreType type, Object first, Object second) {
      this.type = type;
      this.first = first;
      this.second = second;
    }
  }

  static final class NativeIteratorValue {
    final CoreType type;
    final Iterator<Object> iterator;

    NativeIteratorValue(CoreType elementType, Iterator<Object> iterator) {
      type =
          new CoreType.Declared(
              new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.NativeIterator")),
              List.of(elementType),
              CoreValueCategory.IDENTITY,
              CoreNullability.NON_NULL);
      this.iterator = Objects.requireNonNull(iterator, "iterator");
    }
  }

  static final class EnumValue {
    private final DefinitionId definition;
    private final CoreType type;
    private final String enumName;
    private final String variantKey;
    private final List<Object> payload;

    EnumValue(
        DefinitionId definition,
        CoreType type,
        String enumName,
        String variantKey,
        List<Object> payload) {
      this.definition = Objects.requireNonNull(definition, "definition");
      this.type = Objects.requireNonNull(type, "type");
      this.enumName = Objects.requireNonNull(enumName, "enumName");
      this.variantKey = Objects.requireNonNull(variantKey, "variantKey");
      if (variantKey.isBlank()) throw new IllegalArgumentException("variant key must not be blank");
      this.payload = List.copyOf(copyList(payload));
    }

    DefinitionId definition() {
      return definition;
    }

    CoreType type() {
      return type;
    }

    String variantKey() {
      return variantKey;
    }

    Object field(int index) {
      return copy(payload.get(index));
    }

    int fieldCount() {
      return payload.size();
    }

    private boolean sameValue(EnumValue other) {
      return definition.equals(other.definition)
          && type.equals(other.type)
          && variantKey.equals(other.variantKey)
          && equalLists(payload, other.payload);
    }

    @Override
    public boolean equals(Object other) {
      return this == other || other instanceof EnumValue value && sameValue(value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(definition, type, variantKey);
    }

    @Override
    public String toString() {
      if (payload.isEmpty()) return enumName + "." + variantKey;
      return enumName
          + "."
          + variantKey
          + "("
          + payload.stream()
              .map(RuntimeValues::stringify)
              .collect(java.util.stream.Collectors.joining(", "))
          + ")";
    }
  }

  static final class RangeValue {
    final int start;
    final int end;
    final int step;

    RangeValue(int start, int end) {
      this(start, end, 1);
    }

    RangeValue(int start, int end, int step) {
      this.start = start;
      this.end = end;
      this.step = step;
    }

    int size() {
      BigInteger distance =
          step > 0
              ? BigInteger.valueOf(end).subtract(BigInteger.valueOf(start))
              : BigInteger.valueOf(start).subtract(BigInteger.valueOf(end));
      if (distance.signum() <= 0) return 0;
      BigInteger stride = BigInteger.valueOf(step).abs();
      return distance.subtract(BigInteger.ONE).divide(stride).add(BigInteger.ONE).intValueExact();
    }

    Iterator<Object> iterator() {
      return new Iterator<>() {
        private int current = start;
        private boolean exhausted;

        @Override
        public boolean hasNext() {
          return !exhausted && (step > 0 ? current < end : current > end);
        }

        @Override
        public Object next() {
          int value = current;
          try {
            current = Math.addExact(current, step);
          } catch (ArithmeticException exception) {
            exhausted = true;
          }
          return value;
        }
      };
    }
  }

  sealed interface DispatchTarget permits DispatchTarget.Callable, DispatchTarget.Intrinsic {
    record Callable(CallTarget target) implements DispatchTarget {}

    record Intrinsic(IntrinsicId intrinsic) implements DispatchTarget {}
  }

  record ClassInfo(
      DefinitionId definition,
      String name,
      int fieldCount,
      Map<DefinitionId, DispatchTarget> dispatch) {
    ClassInfo {
      dispatch = Map.copyOf(dispatch);
    }
  }

  static final class ObjectValue {
    final ClassInfo classInfo;
    final CoreType type;
    final Object[] fields;

    ObjectValue(ClassInfo classInfo, CoreType type) {
      this.classInfo = classInfo;
      this.type = type;
      fields = new Object[classInfo.fieldCount()];
    }
  }
}
