package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.ConfigurationAbi;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigurationRuntime {
  private static final StructuredValueAccess VALUES = new StructuredValueAccess();

  private final SerializationRuntime serialization;
  private final Map<CoreType, AggregatePlan> plans = new LinkedHashMap<>();
  private DefinitionId keyAnnotation;
  private DefinitionId valueAnnotation;

  ConfigurationRuntime(SerializationRuntime serialization) {
    this.serialization = java.util.Objects.requireNonNull(serialization, "serialization");
  }

  Map<String, Object> properties(CoreType type, Object value) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    write(serialization.shape(type), value, "", properties);
    return properties;
  }

  synchronized int cachedPlanCount() {
    return plans.size();
  }

  private void write(
      SerializationRuntime.Shape source,
      Object value,
      String path,
      LinkedHashMap<String, Object> properties) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      if (value != RuntimeValues.NullValue.INSTANCE) {
        write(nullable.value(), value, path, properties);
      }
      return;
    }
    if (value == RuntimeValues.NullValue.INSTANCE) {
      throw failure(path, "null is not allowed");
    }
    switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> put(properties, path, scalar(scalar, value));
      case SerializationRuntime.EnumShape enumeration ->
          put(properties, path, kebab(enumVariant(enumeration, value)));
      case SerializationRuntime.SequenceShape sequence ->
          writeSequence(sequence, value, path, properties);
      case SerializationRuntime.MapShape map -> writeMap(map, value, path, properties);
      case SerializationRuntime.AggregateShape aggregate ->
          writeAggregate(aggregate, value, path, properties);
      case SerializationRuntime.NullableShape ignored ->
          throw new IllegalStateException("nullable configuration shape was not unwrapped");
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred configuration shape was not resolved");
    }
  }

  private void writeSequence(
      SerializationRuntime.SequenceShape shape,
      Object value,
      String path,
      LinkedHashMap<String, Object> properties) {
    List<Object> values;
    try {
      values = VALUES.sequence(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw failure(path, failure.getMessage());
    }
    SerializationRuntime.AggregateShape named = aggregate(shape.element());
    SerializationRuntime.FieldShape key = named == null ? null : plan(named).key();
    for (int index = 0; index < values.size(); index++) {
      Object element = values.get(index);
      String elementPath;
      if (key == null) {
        elementPath = path + "[" + index + "]";
      } else {
        StructuredValueAccess.AggregateValue aggregate;
        try {
          aggregate = VALUES.aggregate(named, element);
        } catch (StructuredValueAccess.ValueAccessException failure) {
          throw failure(path, failure.getMessage());
        }
        Object keyValue = aggregate.field(key.ordinal());
        if (!(keyValue instanceof String name) || name.isBlank()) {
          throw failure(path, "configuration key must be a non-blank String");
        }
        elementPath = child(path, name);
      }
      write(shape.element(), element, elementPath, properties);
    }
  }

  private void writeMap(
      SerializationRuntime.MapShape shape,
      Object value,
      String path,
      LinkedHashMap<String, Object> properties) {
    SerializationRuntime.Shape keyShape = SerializationRuntime.resolved(shape.key());
    if (!(keyShape instanceof SerializationRuntime.ScalarShape scalar)
        || scalar.kind() != SerializationRuntime.ScalarKind.STRING) {
      throw failure(path, "configuration map key must be String");
    }
    List<StructuredValueAccess.MapEntry> entries;
    try {
      entries = VALUES.map(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw failure(path, failure.getMessage());
    }
    for (StructuredValueAccess.MapEntry entry : entries) {
      if (!(entry.key() instanceof String name) || name.isBlank()) {
        throw failure(path, "configuration map key must be a non-blank String");
      }
      write(shape.value(), entry.value(), child(path, name), properties);
    }
  }

  private void writeAggregate(
      SerializationRuntime.AggregateShape shape,
      Object value,
      String path,
      LinkedHashMap<String, Object> properties) {
    StructuredValueAccess.AggregateValue aggregate;
    try {
      aggregate = VALUES.aggregate(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw failure(path, failure.getMessage());
    }
    AggregatePlan plan = plan(shape);
    SerializationRuntime.FieldShape represented = plan.value();
    if (represented != null) {
      write(represented.shape(), aggregate.field(represented.ordinal()), path, properties);
      return;
    }
    for (PlannedField planned : plan.fields()) {
      SerializationRuntime.FieldShape field = planned.field();
      write(
          field.shape(), aggregate.field(field.ordinal()), child(path, planned.name()), properties);
    }
  }

  private synchronized AggregatePlan plan(SerializationRuntime.AggregateShape shape) {
    AggregatePlan cached = plans.get(shape.type());
    if (cached != null) return cached;
    SerializationRuntime.FieldShape key = null;
    SerializationRuntime.FieldShape value = null;
    List<PlannedField> fields = new java.util.ArrayList<>();
    for (SerializationRuntime.FieldShape field : shape.fields()) {
      if (field.ignored()) continue;
      boolean isKey = hasAnnotation(keyAnnotation(), field);
      boolean isValue = hasAnnotation(valueAnnotation(), field);
      if (isKey && isValue) {
        throw failure("", "configuration field cannot be both key and value");
      }
      if (isKey) {
        if (key != null) throw failure("", "configuration type has multiple key fields");
        key = field;
      }
      if (isValue) {
        if (value != null) throw failure("", "configuration type has multiple value fields");
        value = field;
      }
      if (!isKey) {
        String name = field.coreName().equals(field.name()) ? kebab(field.name()) : field.name();
        fields.add(new PlannedField(field, name));
      }
    }
    if (value != null && shape.fields().stream().filter(field -> !field.ignored()).count() != 1) {
      throw failure("", "configuration value type must contain one stored field");
    }
    AggregatePlan created = new AggregatePlan(key, value, fields);
    plans.put(shape.type(), created);
    return created;
  }

  private boolean hasAnnotation(DefinitionId annotation, SerializationRuntime.FieldShape field) {
    return serialization.hasFieldAnnotation(annotation, field);
  }

  private DefinitionId keyAnnotation() {
    if (keyAnnotation == null) {
      keyAnnotation =
          serialization.annotation(
              ConfigurationAbi.MODULE_NAME,
              ConfigurationAbi.MODULE_VERSION,
              ConfigurationAbi.PACKAGE_NAME,
              ConfigurationAbi.CONFIGURATION_KEY_ANNOTATION_NAME);
    }
    return keyAnnotation;
  }

  private DefinitionId valueAnnotation() {
    if (valueAnnotation == null) {
      valueAnnotation =
          serialization.annotation(
              ConfigurationAbi.MODULE_NAME,
              ConfigurationAbi.MODULE_VERSION,
              ConfigurationAbi.PACKAGE_NAME,
              ConfigurationAbi.CONFIGURATION_VALUE_ANNOTATION_NAME);
    }
    return valueAnnotation;
  }

  private static SerializationRuntime.AggregateShape aggregate(SerializationRuntime.Shape source) {
    SerializationRuntime.Shape shape = SerializationRuntime.resolved(source);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      shape = SerializationRuntime.resolved(nullable.value());
    }
    return shape instanceof SerializationRuntime.AggregateShape aggregate ? aggregate : null;
  }

  private static Object scalar(SerializationRuntime.ScalarShape shape, Object value) {
    return switch (shape.kind()) {
      case STRING -> require(value, String.class, "String");
      case BOOLEAN -> require(value, Boolean.class, "Boolean");
      case INTEGER -> require(value, Integer.class, "Integer");
      case LONG -> require(value, Long.class, "Long");
      case FLOAT -> require(value, Float.class, "Float");
      case DOUBLE -> require(value, Double.class, "Double");
      case CODE_POINT -> {
        if (!(value instanceof RuntimeValues.CodePointValue point)) {
          throw failure("", "expected CodePoint");
        }
        yield point.toString();
      }
    };
  }

  private static Object require(Object value, Class<?> type, String name) {
    if (!type.isInstance(value)) throw failure("", "expected " + name);
    return value;
  }

  private static String enumVariant(SerializationRuntime.EnumShape shape, Object value) {
    try {
      return VALUES.enumVariant(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw failure("", failure.getMessage());
    }
  }

  private static void put(LinkedHashMap<String, Object> properties, String path, Object value) {
    if (path.isBlank()) throw failure(path, "configuration root must be a value");
    if (properties.putIfAbsent(path, value) != null) {
      throw failure(path, "duplicate configuration property");
    }
  }

  private static String child(String parent, String name) {
    if (name.isBlank()) throw failure(parent, "configuration property name is blank");
    return parent.isBlank() ? name : parent + "." + name;
  }

  private static String kebab(String name) {
    StringBuilder result = new StringBuilder(name.length() + 8);
    for (int index = 0; index < name.length(); index++) {
      char current = name.charAt(index);
      if (current == '_' || current == ' ') {
        if (!result.isEmpty() && result.charAt(result.length() - 1) != '-') result.append('-');
        continue;
      }
      boolean upper = Character.isUpperCase(current);
      if (upper
          && index > 0
          && result.charAt(result.length() - 1) != '-'
          && (Character.isLowerCase(name.charAt(index - 1))
              || Character.isDigit(name.charAt(index - 1))
              || (index + 1 < name.length() && Character.isLowerCase(name.charAt(index + 1))))) {
        result.append('-');
      }
      result.append(Character.toLowerCase(current));
    }
    return result.toString();
  }

  private static IllegalArgumentException failure(String path, String message) {
    String location = path.isBlank() ? "$" : path;
    return new IllegalArgumentException(location + ": " + message);
  }

  private record PlannedField(SerializationRuntime.FieldShape field, String name) {}

  private record AggregatePlan(
      SerializationRuntime.FieldShape key,
      SerializationRuntime.FieldShape value,
      List<PlannedField> fields) {
    AggregatePlan {
      fields = List.copyOf(fields);
    }
  }
}
