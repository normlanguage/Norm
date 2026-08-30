package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreEnumVariant;
import java.util.ArrayList;
import java.util.List;

final class StructuredValueAccess {
  List<Object> sequence(SerializationRuntime.SequenceShape shape, Object value) {
    return switch (shape.kind()) {
      case ARRAY -> {
        if (!(value instanceof RuntimeValues.ArrayValue array)
            || !array.type.equals(shape.type())) {
          throw mismatch("Array");
        }
        yield array.values;
      }
      case LIST -> {
        if (!(value instanceof RuntimeValues.ListValue list) || !list.type.equals(shape.type())) {
          throw mismatch("List");
        }
        yield list.values;
      }
    };
  }

  List<MapEntry> map(SerializationRuntime.MapShape shape, Object value) {
    if (!(value instanceof RuntimeValues.MapValue map) || !map.type.equals(shape.type())) {
      throw mismatch("Map");
    }
    List<MapEntry> entries = new ArrayList<>(map.values.size());
    map.values.forEach((key, item) -> entries.add(new MapEntry(key.value, item)));
    return entries;
  }

  AggregateValue aggregate(SerializationRuntime.AggregateShape shape, Object value) {
    if (!(value instanceof RuntimeValues.ObjectValue aggregate)
        || !aggregate.type.equals(shape.type())) {
      throw mismatch(shape.name());
    }
    return new AggregateValue(aggregate.fields);
  }

  String enumVariant(SerializationRuntime.EnumShape shape, Object value) {
    if (!(value instanceof RuntimeValues.EnumValue enumeration)
        || !enumeration.definition().equals(shape.definition())
        || enumeration.fieldCount() != 0) {
      throw mismatch(shape.declaration().nominalType().name());
    }
    return enumeration.variantKey();
  }

  Object sequence(SerializationRuntime.SequenceShape shape, List<Object> values) {
    return shape.kind() == SerializationRuntime.SequenceKind.ARRAY
        ? new RuntimeValues.ArrayValue(shape.type(), values)
        : new RuntimeValues.ListValue(shape.type(), values);
  }

  RuntimeValues.MapValue map(SerializationRuntime.MapShape shape) {
    return new RuntimeValues.MapValue(shape.type());
  }

  void put(RuntimeValues.MapValue map, Object key, Object value) {
    RuntimeValues.mapPut(map, key, value);
  }

  boolean contains(RuntimeValues.MapValue map, Object key) {
    return RuntimeValues.mapContains(map, key);
  }

  Object enumeration(SerializationRuntime.EnumShape shape, CoreEnumVariant variant) {
    return new RuntimeValues.EnumValue(
        shape.definition(),
        shape.type(),
        shape.declaration().nominalType().name(),
        variant.key(),
        List.of());
  }

  Object aggregate(
      SerializationRuntime.AggregateShape shape, Object[] fields, ExecutionState execution) {
    return execution.values().construct(shape.type(), execution, fields);
  }

  private static ValueAccessException mismatch(String expected) {
    return new ValueAccessException(expected);
  }

  record MapEntry(Object key, Object value) {}

  record AggregateValue(Object[] fields) {
    Object field(int ordinal) {
      return fields[ordinal];
    }
  }

  static final class ValueAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String expected;

    ValueAccessException(String expected) {
      super("expected " + expected);
      this.expected = expected;
    }

    String expected() {
      return expected;
    }
  }
}
