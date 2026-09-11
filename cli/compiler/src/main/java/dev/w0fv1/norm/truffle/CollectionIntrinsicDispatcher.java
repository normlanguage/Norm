package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class CollectionIntrinsicDispatcher {
  private CollectionIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case RANGE_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            Object third = arguments.length <= 2 ? null : arguments[2];

            int step = third == null ? 1 : (Integer) third;
            if (step == 0) {
              throw new NormGuestException(
                  RuntimeErrorCode.INVALID_ARGUMENT, "range step must not be zero", location);
            }
            return new RuntimeValues.RangeValue(type, (Integer) first, (Integer) second, step);
          };
      case ARRAY_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.ArrayValue(type, new ArrayList<>());
          };
      case LIST_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.ListValue(type);
          };
      case MAP_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.MapValue(type);
          };
      case SET_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.SetValue(type);
          };
      case STACK_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.StackValue(type);
          };
      case QUEUE_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.QueueValue(type);
          };
      case DEQUE_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.DequeValue(type);
          };
      case PAIR_CONSTRUCT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return new RuntimeValues.PairValue(type, first, second);
          };
      case SIZE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              return RuntimeValues.size(receiver);
            } catch (ArithmeticException exception) {
              throw new NormGuestException(
                  RuntimeErrorCode.INVALID_ARGUMENT, "range size exceeds Integer", location);
            }
          };
      case IS_EMPTY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return isEmpty(receiver);
          };
      case LIST_ADD ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.ListValue) receiver).values.add(RuntimeValues.copy(first));
                return null;
              });
      case LIST_GET ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeValues.copy(
                ((RuntimeValues.ListValue) receiver)
                    .values.get(
                        CollectionBounds.index(
                            first, ((RuntimeValues.ListValue) receiver).values.size(), location)));
          };
      case LIST_REMOVE_AT ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                return RuntimeValues.copy(
                    ((RuntimeValues.ListValue) receiver)
                        .values.remove(
                            CollectionBounds.index(
                                first,
                                ((RuntimeValues.ListValue) receiver).values.size(),
                                location)));
              });
      case ARRAY_FILLED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return new RuntimeValues.ArrayValue(type, filledValues(first, second, location));
          };
      case ARRAY_LAST ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                last(((RuntimeValues.ArrayValue) receiver).values, "Array", location));
          };
      case ARRAY_REVERSED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ArrayValue result =
                (RuntimeValues.ArrayValue) RuntimeValues.copy(receiver);
            Collections.reverse(result.values);
            return result;
          };
      case LIST_FILLED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return new RuntimeValues.ListValue(type, filledValues(first, second, location));
          };
      case LIST_LAST ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                last(((RuntimeValues.ListValue) receiver).values, "List", location));
          };
      case LIST_REMOVE_LAST ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                return RuntimeValues.copy(
                    removeLast(((RuntimeValues.ListValue) receiver).values, "List", location));
              });
      case LIST_REVERSED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ListValue result = (RuntimeValues.ListValue) RuntimeValues.copy(receiver);
            Collections.reverse(result.values);
            return result;
          };
      case MAP_PUT ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                Object second = arguments.length <= 1 ? null : arguments[1];

                RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
                return null;
              });
      case MAP_GET ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeValues.copy(
                RuntimeValues.mapGetOrNull((RuntimeValues.MapValue) receiver, first));
          };
      case MAP_CONTAINS_KEY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeValues.mapContains((RuntimeValues.MapValue) receiver, first);
          };
      case MAP_REMOVE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                return RuntimeValues.mapRemove((RuntimeValues.MapValue) receiver, first);
              });
      case SET_ADD ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                return RuntimeValues.setAdd((RuntimeValues.SetValue) receiver, first);
              });
      case SET_CONTAINS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeValues.setContains((RuntimeValues.SetValue) receiver, first);
          };
      case SET_REMOVE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                return RuntimeValues.setRemove((RuntimeValues.SetValue) receiver, first);
              });
      case STACK_PUSH ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.StackValue) receiver).values.push(RuntimeValues.copy(first));
                return null;
              });
      case STACK_POP ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                return RuntimeValues.copy(
                    requireElement(
                        ((RuntimeValues.StackValue) receiver).values.pollFirst(),
                        "Stack",
                        location));
              });
      case STACK_PEEK ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                requireElement(
                    ((RuntimeValues.StackValue) receiver).values.peekFirst(), "Stack", location));
          };
      case QUEUE_ADD ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.QueueValue) receiver).values.addLast(RuntimeValues.copy(first));
                return null;
              });
      case QUEUE_REMOVE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                return RuntimeValues.copy(
                    requireElement(
                        ((RuntimeValues.QueueValue) receiver).values.pollFirst(),
                        "Queue",
                        location));
              });
      case QUEUE_PEEK ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                requireElement(
                    ((RuntimeValues.QueueValue) receiver).values.peekFirst(), "Queue", location));
          };
      case DEQUE_ADD_FIRST ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.DequeValue) receiver).values.addFirst(RuntimeValues.copy(first));
                return null;
              });
      case DEQUE_ADD_LAST ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.DequeValue) receiver).values.addLast(RuntimeValues.copy(first));
                return null;
              });
      case DEQUE_REMOVE_FIRST ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                return RuntimeValues.copy(
                    requireElement(
                        ((RuntimeValues.DequeValue) receiver).values.pollFirst(),
                        "Deque",
                        location));
              });
      case DEQUE_REMOVE_LAST ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                return RuntimeValues.copy(
                    requireElement(
                        ((RuntimeValues.DequeValue) receiver).values.pollLast(),
                        "Deque",
                        location));
              });
      case DEQUE_PEEK_FIRST ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                requireElement(
                    ((RuntimeValues.DequeValue) receiver).values.peekFirst(), "Deque", location));
          };
      case DEQUE_PEEK_LAST ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return RuntimeValues.copy(
                requireElement(
                    ((RuntimeValues.DequeValue) receiver).values.peekLast(), "Deque", location));
          };
      case PAIR_FIRST_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return RuntimeValues.copy(((RuntimeValues.PairValue) receiver).first);
          };
      case PAIR_SECOND_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object second = arguments.length <= 1 ? null : arguments[1];
            return RuntimeValues.copy(((RuntimeValues.PairValue) receiver).second);
          };
      case PAIR_FIRST_WRITE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];

                ((RuntimeValues.PairValue) receiver).first = RuntimeValues.copy(first);
                return null;
              });
      case PAIR_SECOND_WRITE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                Object second = arguments.length <= 1 ? null : arguments[1];

                ((RuntimeValues.PairValue) receiver).second = RuntimeValues.copy(first);
                return null;
              });
      case ARRAY_INDEX_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return ((RuntimeValues.ArrayValue) receiver)
                .values.get(
                    CollectionBounds.index(
                        first, ((RuntimeValues.ArrayValue) receiver).values.size(), location));
          };
      case LIST_INDEX_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return ((RuntimeValues.ListValue) receiver)
                .values.get(
                    CollectionBounds.index(
                        first, ((RuntimeValues.ListValue) receiver).values.size(), location));
          };
      case MAP_INDEX_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            return mapGet((RuntimeValues.MapValue) receiver, first, location);
          };
      case ARRAY_INDEX_WRITE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                Object second = arguments.length <= 1 ? null : arguments[1];

                ((RuntimeValues.ArrayValue) receiver)
                    .values.set(
                        CollectionBounds.index(
                            first, ((RuntimeValues.ArrayValue) receiver).values.size(), location),
                        RuntimeValues.copy(second));
                return null;
              });
      case LIST_INDEX_WRITE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                Object second = arguments.length <= 1 ? null : arguments[1];

                ((RuntimeValues.ListValue) receiver)
                    .values.set(
                        CollectionBounds.index(
                            first, ((RuntimeValues.ListValue) receiver).values.size(), location),
                        RuntimeValues.copy(second));
                return null;
              });
      case MAP_INDEX_WRITE ->
          ValueObservations.mutation(
              (receiver, arguments, type, context, location, annotations, execution) -> {
                Object first = arguments.length <= 0 ? null : arguments[0];
                Object second = arguments.length <= 1 ? null : arguments[1];

                RuntimeValues.mapPut((RuntimeValues.MapValue) receiver, first, second);
                return null;
              });
      case ARRAY_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.ArrayValue) receiver).type,
                ((RuntimeValues.ArrayValue) receiver).values.iterator());
          };
      case LIST_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.ListValue) receiver).type,
                ((RuntimeValues.ListValue) receiver).values.iterator());
          };
      case MAP_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.NativeIteratorValue(
                mapElementType(((RuntimeValues.MapValue) receiver).type),
                mapIterator((RuntimeValues.MapValue) receiver));
          };
      case SET_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.SetValue) receiver).type,
                ((RuntimeValues.SetValue) receiver)
                    .values.stream().map(key -> key.value).iterator());
          };
      case STACK_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.StackValue) receiver).type,
                ((RuntimeValues.StackValue) receiver).values.iterator());
          };
      case QUEUE_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.QueueValue) receiver).type,
                ((RuntimeValues.QueueValue) receiver).values.iterator());
          };
      case DEQUE_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return nativeIterator(
                ((RuntimeValues.DequeValue) receiver).type,
                ((RuntimeValues.DequeValue) receiver).values.iterator());
          };
      case RANGE_ITERATOR ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return new RuntimeValues.NativeIteratorValue(
                CoreType.INTEGER, ((RuntimeValues.RangeValue) receiver).iterator());
          };
      case ITERATOR_HAS_NEXT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.NativeIteratorValue) receiver).iterator.hasNext();
          };
      case ITERATOR_NEXT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Iterator<Object> iterator = ((RuntimeValues.NativeIteratorValue) receiver).iterator;
            if (!iterator.hasNext()) {
              throw new NormGuestException(
                  RuntimeErrorCode.EMPTY_COLLECTION, "iterator is exhausted", location);
            }
            return RuntimeValues.copy(iterator.next());
          };
      default ->
          throw new IllegalArgumentException("Unsupported collection intrinsic: " + intrinsic);
    };
  }

  private static RuntimeValues.NativeIteratorValue nativeIterator(
      CoreType collectionType, Iterator<Object> iterator) {
    CoreType.Declared declared = (CoreType.Declared) collectionType;
    return new RuntimeValues.NativeIteratorValue(declared.arguments().getFirst(), iterator);
  }

  private static CoreType mapElementType(CoreType mapType) {
    CoreType.Declared map = (CoreType.Declared) mapType;
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Pair")),
        map.arguments(),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static List<Object> filledValues(Object sizeValue, Object value, Node location) {
    int size = (Integer) sizeValue;
    if (size < 0) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "collection size is outside 0..2147483647", location);
    }
    List<Object> result = new ArrayList<>(size);
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
    Iterator<Map.Entry<RuntimeValues.RuntimeKey, Object>> entries =
        map.values.entrySet().iterator();
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
        Map.Entry<RuntimeValues.RuntimeKey, Object> entry = entries.next();
        return new RuntimeValues.PairValue(pairType, entry.getKey().value, entry.getValue());
      }
    };
  }
}
