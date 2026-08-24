package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IntrinsicDispatcher {
  private static final Set<IntrinsicId> SUPPORTED =
      Collections.unmodifiableSet(EnumSet.allOf(IntrinsicId.class));

  private IntrinsicDispatcher() {}

  public static Set<IntrinsicId> supportedIntrinsics() {
    return SUPPORTED;
  }

  static Object execute(
      IntrinsicId intrinsic,
      Object receiver,
      Object[] arguments,
      CoreType type,
      ExecutionContext context,
      Node location) {
    Object first = arguments.length == 0 ? null : arguments[0];
    Object second = arguments.length < 2 ? null : arguments[1];
    Object third = arguments.length < 3 ? null : arguments[2];
    return switch (intrinsic) {
      case PRINT_LINE -> {
        context.output().println(RuntimeValues.stringify(first));
        yield null;
      }
      case EXPECTED_OUTPUT_LINE -> {
        context.expectedOutput().println(RuntimeValues.stringify(first));
        yield null;
      }
      case RANGE_CONSTRUCT -> {
        long step = third == null ? 1 : (Long) third;
        if (step == 0) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "range step must not be zero", location);
        }
        yield new RuntimeValues.RangeValue((Long) first, (Long) second, step);
      }
      case ARRAY_CONSTRUCT -> new RuntimeValues.ArrayValue(type, new ArrayList<>());
      case LIST_CONSTRUCT -> new RuntimeValues.ListValue(type);
      case MAP_CONSTRUCT -> new RuntimeValues.MapValue(type);
      case SET_CONSTRUCT -> new RuntimeValues.SetValue(type);
      case STACK_CONSTRUCT -> new RuntimeValues.StackValue(type);
      case QUEUE_CONSTRUCT -> new RuntimeValues.QueueValue(type);
      case DEQUE_CONSTRUCT -> new RuntimeValues.DequeValue(type);
      case PAIR_CONSTRUCT -> new RuntimeValues.PairValue(type, first, second);
      case STRING_BUILDER_CONSTRUCT -> new RuntimeValues.BuilderValue();
      case SIZE -> {
        try {
          yield RuntimeValues.size(receiver);
        } catch (ArithmeticException exception) {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "range size exceeds Integer", location);
        }
      }
      case IS_EMPTY -> isEmpty(receiver);
      case LIST_ADD -> {
        ((RuntimeValues.ListValue) receiver).values.add(RuntimeValues.copy(first));
        yield null;
      }
      case LIST_GET ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case LIST_REMOVE_AT ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.remove(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case ARRAY_FILLED ->
          new RuntimeValues.ArrayValue(type, filledValues(first, second, location));
      case ARRAY_LAST ->
          RuntimeValues.copy(last(((RuntimeValues.ArrayValue) receiver).values, "Array", location));
      case ARRAY_REVERSED -> {
        RuntimeValues.ArrayValue result = (RuntimeValues.ArrayValue) RuntimeValues.copy(receiver);
        Collections.reverse(result.values);
        yield result;
      }
      case LIST_FILLED -> new RuntimeValues.ListValue(type, filledValues(first, second, location));
      case LIST_LAST ->
          RuntimeValues.copy(last(((RuntimeValues.ListValue) receiver).values, "List", location));
      case LIST_REMOVE_LAST ->
          RuntimeValues.copy(
              removeLast(((RuntimeValues.ListValue) receiver).values, "List", location));
      case LIST_REVERSED -> {
        RuntimeValues.ListValue result = (RuntimeValues.ListValue) RuntimeValues.copy(receiver);
        Collections.reverse(result.values);
        yield result;
      }
      case MAP_PUT -> {
        RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
        yield null;
      }
      case MAP_GET ->
          RuntimeValues.copy(RuntimeValues.mapGetOrNull((RuntimeValues.MapValue) receiver, first));
      case MAP_CONTAINS_KEY -> RuntimeValues.mapContains((RuntimeValues.MapValue) receiver, first);
      case MAP_REMOVE -> RuntimeValues.mapRemove((RuntimeValues.MapValue) receiver, first);
      case SET_ADD -> RuntimeValues.setAdd((RuntimeValues.SetValue) receiver, first);
      case SET_CONTAINS -> RuntimeValues.setContains((RuntimeValues.SetValue) receiver, first);
      case SET_REMOVE -> RuntimeValues.setRemove((RuntimeValues.SetValue) receiver, first);
      case STACK_PUSH -> {
        ((RuntimeValues.StackValue) receiver).values.push(RuntimeValues.copy(first));
        yield null;
      }
      case STACK_POP ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.StackValue) receiver).values.pollFirst(), "Stack", location));
      case STACK_PEEK ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.StackValue) receiver).values.peekFirst(), "Stack", location));
      case QUEUE_ADD -> {
        ((RuntimeValues.QueueValue) receiver).values.addLast(RuntimeValues.copy(first));
        yield null;
      }
      case QUEUE_REMOVE ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.QueueValue) receiver).values.pollFirst(), "Queue", location));
      case QUEUE_PEEK ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.QueueValue) receiver).values.peekFirst(), "Queue", location));
      case DEQUE_ADD_FIRST -> {
        ((RuntimeValues.DequeValue) receiver).values.addFirst(RuntimeValues.copy(first));
        yield null;
      }
      case DEQUE_ADD_LAST -> {
        ((RuntimeValues.DequeValue) receiver).values.addLast(RuntimeValues.copy(first));
        yield null;
      }
      case DEQUE_REMOVE_FIRST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.pollFirst(), "Deque", location));
      case DEQUE_REMOVE_LAST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.pollLast(), "Deque", location));
      case DEQUE_PEEK_FIRST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.peekFirst(), "Deque", location));
      case DEQUE_PEEK_LAST ->
          RuntimeValues.copy(
              requireElement(
                  ((RuntimeValues.DequeValue) receiver).values.peekLast(), "Deque", location));
      case BUILDER_APPEND -> {
        RuntimeValues.BuilderValue builder = (RuntimeValues.BuilderValue) receiver;
        builder.value.append(RuntimeValues.stringify(first));
        yield builder;
      }
      case BUILDER_TO_STRING -> ((RuntimeValues.BuilderValue) receiver).value.toString();
      case STRING_BYTE_SIZE -> RuntimeValues.byteSize((String) receiver);
      case STRING_CODE_POINT_SIZE -> RuntimeValues.codePointSize((String) receiver);
      case STRING_GRAPHEME_SIZE -> RuntimeValues.graphemeSize((String) receiver);
      case STRING_CODE_POINTS -> RuntimeValues.codePoints((String) receiver);
      case STRING_GRAPHEMES -> RuntimeValues.graphemes((String) receiver);
      case STRING_SLICE_CODE_POINTS ->
          RuntimeValues.sliceCodePoints((String) receiver, (Long) first, (Long) second, location);
      case STRING_SPLIT -> RuntimeValues.split((String) receiver, (String) first, location);
      case STRING_IS_EMPTY -> ((String) receiver).isEmpty();
      case STRING_CONTAINS -> ((String) receiver).contains((String) first);
      case STRING_STARTS_WITH -> ((String) receiver).startsWith((String) first);
      case STRING_ENDS_WITH -> ((String) receiver).endsWith((String) first);
      case STRING_SLICE_GRAPHEMES ->
          RuntimeValues.sliceGraphemes((String) receiver, (Long) first, (Long) second, location);
      case STRING_REPLACE ->
          RuntimeValues.replace((String) receiver, (String) first, (String) second, location);
      case STRING_REPLACE_FIRST ->
          RuntimeValues.replaceFirst((String) receiver, (String) first, (String) second, location);
      case STRING_TRIM -> RuntimeValues.trim((String) receiver);
      case STRING_TRIM_START -> RuntimeValues.trimStart((String) receiver);
      case STRING_TRIM_END -> RuntimeValues.trimEnd((String) receiver);
      case STRING_TO_LOWERCASE -> RuntimeValues.toLowercase((String) receiver);
      case STRING_TO_UPPERCASE -> RuntimeValues.toUppercase((String) receiver);
      case STRING_EQUALS_IGNORE_CASE_ASCII ->
          RuntimeValues.equalsIgnoreCaseAscii((String) receiver, (String) first);
      case STRING_COMPARE_CODE_POINTS ->
          RuntimeValues.compareCodePoints((String) receiver, (String) first);
      case STRING_NORMALIZE_NFC -> RuntimeValues.normalizeNfc((String) receiver);
      case STRING_NORMALIZE_NFD -> RuntimeValues.normalizeNfd((String) receiver);
      case STRING_NORMALIZE_NFKC -> RuntimeValues.normalizeNfkc((String) receiver);
      case STRING_NORMALIZE_NFKD -> RuntimeValues.normalizeNfkd((String) receiver);
      case STRING_IS_NORMALIZED_NFC -> RuntimeValues.isNormalizedNfc((String) receiver);
      case STRING_IS_NORMALIZED_NFD -> RuntimeValues.isNormalizedNfd((String) receiver);
      case STRING_IS_NORMALIZED_NFKC -> RuntimeValues.isNormalizedNfkc((String) receiver);
      case STRING_IS_NORMALIZED_NFKD -> RuntimeValues.isNormalizedNfkd((String) receiver);
      case CODE_POINT_SCALAR_VALUE -> (long) ((RuntimeValues.CodePointValue) receiver).value();
      case CODE_POINT_IS_DECIMAL_DIGIT ->
          Character.isDigit(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_LETTER ->
          Character.isLetter(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_WHITESPACE ->
          RuntimeValues.isWhitespace(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_UPPERCASE ->
          Character.isUpperCase(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_LOWERCASE ->
          Character.isLowerCase(((RuntimeValues.CodePointValue) receiver).value());
      case CODE_POINT_IS_ASCII_DIGIT -> {
        int value = ((RuntimeValues.CodePointValue) receiver).value();
        yield value >= '0' && value <= '9';
      }
      case CODE_POINT_ASCII_DIGIT_VALUE -> {
        int value = ((RuntimeValues.CodePointValue) receiver).value();
        if (value < '0' || value > '9') {
          throw new NormGuestException(
              RuntimeErrorCode.INVALID_ARGUMENT, "code point is not an ASCII digit", location);
        }
        yield (long) (value - '0');
      }
      case PAIR_FIRST_READ -> RuntimeValues.copy(((RuntimeValues.PairValue) receiver).first);
      case PAIR_SECOND_READ -> RuntimeValues.copy(((RuntimeValues.PairValue) receiver).second);
      case PAIR_FIRST_WRITE -> {
        ((RuntimeValues.PairValue) receiver).first = RuntimeValues.copy(first);
        yield null;
      }
      case PAIR_SECOND_WRITE -> {
        ((RuntimeValues.PairValue) receiver).second = RuntimeValues.copy(first);
        yield null;
      }
      case ARRAY_INDEX_READ ->
          RuntimeValues.copy(
              ((RuntimeValues.ArrayValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ArrayValue) receiver).values.size(), location)));
      case LIST_INDEX_READ ->
          RuntimeValues.copy(
              ((RuntimeValues.ListValue) receiver)
                  .values.get(
                      index(first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
      case MAP_INDEX_READ ->
          RuntimeValues.copy(mapGet((RuntimeValues.MapValue) receiver, first, location));
      case ARRAY_INDEX_WRITE -> {
        ((RuntimeValues.ArrayValue) receiver)
            .values.set(
                index(first, ((RuntimeValues.ArrayValue) receiver).values.size(), location),
                RuntimeValues.copy(second));
        yield null;
      }
      case LIST_INDEX_WRITE -> {
        ((RuntimeValues.ListValue) receiver)
            .values.set(
                index(first, ((RuntimeValues.ListValue) receiver).values.size(), location),
                RuntimeValues.copy(second));
        yield null;
      }
      case MAP_INDEX_WRITE -> {
        RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
        yield null;
      }
      case ARRAY_ITERATOR -> ((RuntimeValues.ArrayValue) receiver).values.iterator();
      case LIST_ITERATOR -> ((RuntimeValues.ListValue) receiver).values.iterator();
      case MAP_ITERATOR -> mapIterator((RuntimeValues.MapValue) receiver);
      case SET_ITERATOR -> ((RuntimeValues.SetValue) receiver).values.iterator();
      case STACK_ITERATOR -> ((RuntimeValues.StackValue) receiver).values.iterator();
      case QUEUE_ITERATOR -> ((RuntimeValues.QueueValue) receiver).values.iterator();
      case DEQUE_ITERATOR -> ((RuntimeValues.DequeValue) receiver).values.iterator();
      case RANGE_ITERATOR -> ((RuntimeValues.RangeValue) receiver).iterator();
    };
  }

  private static int index(Object value, int size, Node location) {
    long index = (Long) value;
    if (index < 0 || index >= size) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS,
          "index " + index + " is outside collection size " + size,
          location);
    }
    return Math.toIntExact(index);
  }

  private static List<Object> filledValues(Object sizeValue, Object value, Node location) {
    long size = (Long) sizeValue;
    if (size < 0 || size > Integer.MAX_VALUE) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "collection size is outside 0..2147483647", location);
    }
    List<Object> result = new ArrayList<>((int) size);
    for (int index = 0; index < size; index++) result.add(RuntimeValues.copy(value));
    return result;
  }

  private static Object last(List<Object> values, String collection, Node location) {
    return requireElement(values.isEmpty() ? null : values.getLast(), collection, location);
  }

  private static Object removeLast(List<Object> values, String collection, Node location) {
    return requireElement(values.isEmpty() ? null : values.removeLast(), collection, location);
  }

  private static Object mapGet(RuntimeValues.MapValue map, Object key, Node location) {
    if (!RuntimeValues.mapContains(map, key)) {
      throw new NormGuestException(
          RuntimeErrorCode.MISSING_MAP_KEY, "map key does not exist", location);
    }
    return RuntimeValues.mapGet(map, key);
  }

  private static Object requireElement(Object value, String collection, Node location) {
    if (value == null) {
      throw new NormGuestException(
          RuntimeErrorCode.EMPTY_COLLECTION, collection + " is empty", location);
    }
    return value;
  }

  private static boolean isEmpty(Object value) {
    return switch (value) {
      case RuntimeValues.ListValue list -> list.values.isEmpty();
      case RuntimeValues.MapValue map -> map.values.isEmpty();
      case RuntimeValues.SetValue set -> set.values.isEmpty();
      case RuntimeValues.StackValue stack -> stack.values.isEmpty();
      case RuntimeValues.QueueValue queue -> queue.values.isEmpty();
      case RuntimeValues.DequeValue deque -> deque.values.isEmpty();
      default -> throw new IllegalStateException("invalid isEmpty receiver");
    };
  }

  private static Iterator<Object> mapIterator(RuntimeValues.MapValue map) {
    Iterator<Map.Entry<Object, Object>> entries = map.values.entrySet().iterator();
    if (!(map.type instanceof CoreType.Declared mapType)) {
      throw new IllegalStateException("map runtime type is not declared");
    }
    CoreType pairType =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Pair")),
            mapType.arguments(),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return entries.hasNext();
      }

      @Override
      public Object next() {
        Map.Entry<Object, Object> entry = entries.next();
        return new RuntimeValues.PairValue(pairType, entry.getKey(), entry.getValue());
      }
    };
  }
}
