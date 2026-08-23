package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
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
      SemanticType type,
      ExecutionContext context,
      Node location) {
    Object first = arguments.length == 0 ? null : arguments[0];
    Object second = arguments.length < 2 ? null : arguments[1];
    return switch (intrinsic) {
      case PRINT_LINE -> {
        context.output().println(RuntimeValues.stringify(first));
        yield null;
      }
      case EXPECTED_OUTPUT_LINE -> {
        context.expectedOutput().println(RuntimeValues.stringify(first));
        yield null;
      }
      case RANGE_CONSTRUCT -> new RuntimeValues.RangeValue((Long) first, (Long) second);
      case ARRAY_CONSTRUCT -> new RuntimeValues.ArrayValue(type, new ArrayList<>());
      case LIST_CONSTRUCT -> new RuntimeValues.ListValue(type);
      case MAP_CONSTRUCT -> new RuntimeValues.MapValue(type);
      case SET_CONSTRUCT -> new RuntimeValues.SetValue(type);
      case STACK_CONSTRUCT -> new RuntimeValues.StackValue(type);
      case QUEUE_CONSTRUCT -> new RuntimeValues.QueueValue(type);
      case DEQUE_CONSTRUCT -> new RuntimeValues.DequeValue(type);
      case PAIR_CONSTRUCT -> new RuntimeValues.PairValue(type, first, second);
      case STRING_BUILDER_CONSTRUCT -> new RuntimeValues.BuilderValue();
      case SIZE -> RuntimeValues.size(receiver);
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
      case MAP_PUT -> {
        RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
        yield null;
      }
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
    SemanticType pairType =
        SemanticType.declared(
            "std.core.Pair",
            "Pair",
            map.type.arguments(),
            dev.w0fv1.norm.semantic.ValueCategory.VALUE);
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
