package dev.w0fv1.norm.truffle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreEnumVariant;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

final class JacksonDataRuntime {
  private static final StructuredValueAccess VALUES = new StructuredValueAccess();
  static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
  static final int MAX_STRING_LENGTH = 1024 * 1024;
  static final int MAX_ELEMENTS = 1_000_000;
  static final int MAX_DEPTH = 128;

  private final String format;
  private final String codePrefix;
  private final JsonFactory factory;
  private final Predicate<JsonParser> unsupportedToken;
  private final FailureFactory failures;

  JacksonDataRuntime(
      String format,
      JsonFactory factory,
      Predicate<JsonParser> unsupportedToken,
      FailureFactory failures) {
    this.format = java.util.Objects.requireNonNull(format, "format");
    codePrefix = "NORM-" + format;
    this.factory = java.util.Objects.requireNonNull(factory, "factory");
    this.unsupportedToken = java.util.Objects.requireNonNull(unsupportedToken, "unsupportedToken");
    this.failures = java.util.Objects.requireNonNull(failures, "failures");
  }

  String encode(
      Object value, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    try {
      StringWriter output = new StringWriter();
      try (JsonGenerator writer = factory.createGenerator(output)) {
        write(writer, shape, value, "$", 0);
      }
      return output.toString();
    } catch (Failure failure) {
      throw failure(failure, execution, location);
    } catch (IOException failure) {
      throw failure(
          code("WRITE"),
          message(failure, "cannot write " + format),
          "$",
          location(failure),
          execution,
          location);
    }
  }

  Object decode(
      String input, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
      throw failure(
          code("LIMIT"),
          format + " input exceeds the byte limit",
          "$",
          Location.START,
          execution,
          location);
    }
    try (JsonParser reader = factory.createParser(input.getBytes(StandardCharsets.UTF_8))) {
      if (reader.nextToken() == null) {
        throw new Failure(code("SYNTAX"), "$", "expected a " + format + " value");
      }
      Object result = read(reader, shape, "$", 0, execution);
      if (reader.nextToken() != null) {
        throw new Failure(code("SYNTAX"), "$", "multiple " + format + " documents are not allowed");
      }
      return result;
    } catch (Failure failure) {
      throw failure(failure, execution, location);
    } catch (StreamConstraintsException failure) {
      throw failure(
          code("LIMIT"),
          failure.getMessage(),
          "$",
          location(failure.getLocation()),
          execution,
          location);
    } catch (IOException failure) {
      throw failure(
          code("SYNTAX"),
          message(failure, "invalid " + format),
          "$",
          location(failure),
          execution,
          location);
    }
  }

  NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return failures.create(
        failure.code().replace("NORM-SERIALIZATION-", codePrefix + "-"),
        failure.getMessage(),
        failure.path(),
        0,
        1,
        1,
        execution,
        location);
  }

  private void write(
      JsonGenerator writer, SerializationRuntime.Shape shape, Object value, String path, int depth)
      throws IOException {
    shape = SerializationRuntime.resolved(shape);
    requireDepth(path, depth);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      if (value == RuntimeValues.NullValue.INSTANCE) {
        writer.writeNull();
      } else {
        write(writer, nullable.value(), value, path, depth);
      }
      return;
    }
    if (value == RuntimeValues.NullValue.INSTANCE) {
      throw new Failure(code("NULL"), path, "null is not allowed");
    }
    switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> writeScalar(writer, scalar, value, path);
      case SerializationRuntime.SequenceShape sequence -> {
        List<Object> values = sequenceValues(sequence, value, path);
        requireElements(values.size(), path);
        writer.writeStartArray();
        for (int index = 0; index < values.size(); index++) {
          write(writer, sequence.element(), values.get(index), index(path, index), depth + 1);
        }
        writer.writeEndArray();
      }
      case SerializationRuntime.MapShape map -> {
        requireStringMapKey(map, path);
        List<StructuredValueAccess.MapEntry> entries = mapValues(map, value, path);
        requireElements(entries.size(), path);
        writer.writeStartObject();
        for (StructuredValueAccess.MapEntry entry : entries) {
          if (!(entry.key() instanceof String name)) {
            throw new Failure(code("MAP-KEY"), path, format + " mapping key must be String");
          }
          writer.writeFieldName(name);
          write(writer, map.value(), entry.value(), field(path, name), depth + 1);
        }
        writer.writeEndObject();
      }
      case SerializationRuntime.EnumShape enumeration ->
          writer.writeString(enumVariant(enumeration, value, path));
      case SerializationRuntime.AggregateShape aggregate -> {
        StructuredValueAccess.AggregateValue object = aggregateValue(aggregate, value, path);
        writer.writeStartObject();
        for (SerializationRuntime.FieldShape field : aggregate.fields()) {
          if (field.ignored()) continue;
          writer.writeFieldName(field.name());
          write(
              writer,
              field.shape(),
              object.field(field.ordinal()),
              field(path, field.name()),
              depth + 1);
        }
        writer.writeEndObject();
      }
      case SerializationRuntime.NullableShape ignored ->
          throw new IllegalStateException("nullable shape was not unwrapped");
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
    }
  }

  private Object read(
      JsonParser reader,
      SerializationRuntime.Shape shape,
      String path,
      int depth,
      ExecutionState execution)
      throws IOException {
    shape = SerializationRuntime.resolved(shape);
    requireDepth(path, depth);
    rejectNativeMetadata(reader, path);
    if (shape instanceof SerializationRuntime.NullableShape nullable) {
      if (reader.currentToken() == JsonToken.VALUE_NULL) {
        return RuntimeValues.NullValue.INSTANCE;
      }
      return read(reader, nullable.value(), path, depth, execution);
    }
    return switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> readScalar(reader, scalar, path);
      case SerializationRuntime.SequenceShape sequence -> {
        requireToken(reader, JsonToken.START_ARRAY, path);
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        while (reader.nextToken() != JsonToken.END_ARRAY) {
          requireElements(values.size() + 1, path);
          values.add(
              read(reader, sequence.element(), index(path, values.size()), depth + 1, execution));
        }
        yield VALUES.sequence(sequence, values);
      }
      case SerializationRuntime.MapShape map -> {
        requireStringMapKey(map, path);
        requireToken(reader, JsonToken.START_OBJECT, path);
        RuntimeValues.MapValue value = VALUES.map(map);
        int count = 0;
        while (reader.nextToken() != JsonToken.END_OBJECT) {
          requireElements(++count, path);
          requireToken(reader, JsonToken.FIELD_NAME, path);
          rejectNativeMetadata(reader, path);
          String name = reader.currentName();
          if (VALUES.contains(value, name)) {
            throw new Failure(code("DUPLICATE-KEY"), field(path, name), "duplicate mapping key");
          }
          reader.nextToken();
          VALUES.put(
              value, name, read(reader, map.value(), field(path, name), depth + 1, execution));
        }
        yield value;
      }
      case SerializationRuntime.EnumShape enumeration -> {
        String variant = string(reader, path);
        CoreEnumVariant declaration =
            enumeration.declaration().variants().stream()
                .filter(candidate -> candidate.key().equals(variant))
                .findFirst()
                .orElseThrow(
                    () ->
                        new Failure(code("ENUM"), path, "unknown enum variant '" + variant + "'"));
        yield VALUES.enumeration(enumeration, declaration);
      }
      case SerializationRuntime.AggregateShape aggregate ->
          readAggregate(reader, aggregate, path, depth, execution);
      case SerializationRuntime.NullableShape ignored ->
          throw new IllegalStateException("nullable shape was not unwrapped");
      case SerializationRuntime.DeferredShape ignored ->
          throw new IllegalStateException("deferred shape was not resolved");
    };
  }

  private Object readAggregate(
      JsonParser reader,
      SerializationRuntime.AggregateShape aggregate,
      String path,
      int depth,
      ExecutionState execution)
      throws IOException {
    requireToken(reader, JsonToken.START_OBJECT, path);
    Object[] fields = new Object[aggregate.fields().size()];
    boolean[] seen = new boolean[aggregate.fields().size()];
    int count = 0;
    while (reader.nextToken() != JsonToken.END_OBJECT) {
      requireElements(++count, path);
      requireToken(reader, JsonToken.FIELD_NAME, path);
      rejectNativeMetadata(reader, path);
      String name = reader.currentName();
      Integer ordinal = aggregate.names().get(name);
      if (ordinal == null) {
        throw new Failure(code("UNKNOWN-FIELD"), field(path, name), "unknown field '" + name + "'");
      }
      if (seen[ordinal]) {
        throw new Failure(
            code("DUPLICATE-FIELD"), field(path, name), "duplicate field '" + name + "'");
      }
      SerializationRuntime.FieldShape field = aggregate.fields().get(ordinal);
      reader.nextToken();
      fields[ordinal] = read(reader, field.shape(), field(path, name), depth + 1, execution);
      seen[ordinal] = true;
    }
    for (SerializationRuntime.FieldShape field : aggregate.fields()) {
      if (field.ignored()) {
        fields[field.ordinal()] = RuntimeValues.NullValue.INSTANCE;
      } else if (!seen[field.ordinal()]) {
        if (SerializationRuntime.resolved(field.shape())
            instanceof SerializationRuntime.NullableShape) {
          fields[field.ordinal()] = RuntimeValues.NullValue.INSTANCE;
        } else {
          throw new Failure(
              code("MISSING-FIELD"), field(path, field.name()), "required field is missing");
        }
      }
    }
    return VALUES.aggregate(aggregate, fields, execution);
  }

  private void writeScalar(
      JsonGenerator writer, SerializationRuntime.ScalarShape shape, Object value, String path)
      throws IOException {
    switch (shape.kind()) {
      case STRING -> {
        if (!(value instanceof String string)) throw typeMismatch(path, "String");
        requireString(string, path);
        writer.writeString(string);
      }
      case BOOLEAN -> {
        if (!(value instanceof Boolean bool)) throw typeMismatch(path, "Boolean");
        writer.writeBoolean(bool);
      }
      case INTEGER -> {
        if (!(value instanceof Integer integer)) throw typeMismatch(path, "Integer");
        writer.writeNumber(integer);
      }
      case LONG -> {
        if (!(value instanceof Long number)) throw typeMismatch(path, "Long");
        writer.writeNumber(number);
      }
      case FLOAT -> {
        if (!(value instanceof Float number) || !Float.isFinite(number)) {
          throw new Failure(code("NUMBER"), path, "number must be a finite Float");
        }
        writer.writeNumber(number);
      }
      case DOUBLE -> {
        if (!(value instanceof Double number) || !Double.isFinite(number)) {
          throw new Failure(code("NUMBER"), path, "number must be a finite Double");
        }
        writer.writeNumber(number);
      }
      case CODE_POINT -> {
        if (!(value instanceof RuntimeValues.CodePointValue point)) {
          throw typeMismatch(path, "CodePoint");
        }
        writer.writeString(point.toString());
      }
    }
  }

  private Object readScalar(JsonParser reader, SerializationRuntime.ScalarShape shape, String path)
      throws IOException {
    return switch (shape.kind()) {
      case STRING -> string(reader, path);
      case BOOLEAN -> {
        JsonToken token = reader.currentToken();
        if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
          throw typeMismatch(path, "boolean");
        }
        yield reader.getBooleanValue();
      }
      case INTEGER -> parseInteger(number(reader, path), path);
      case LONG -> parseLong(number(reader, path), path);
      case FLOAT -> parseFloat(number(reader, path), path);
      case DOUBLE -> parseDouble(number(reader, path), path);
      case CODE_POINT -> {
        String value = string(reader, path);
        if (value.codePointCount(0, value.length()) != 1) {
          throw new Failure(code("CODE-POINT"), path, "expected one Unicode code point");
        }
        yield new RuntimeValues.CodePointValue(value.codePointAt(0));
      }
    };
  }

  private void requireStringMapKey(SerializationRuntime.MapShape map, String path) {
    SerializationRuntime.Shape key = SerializationRuntime.resolved(map.key());
    if (!(key instanceof SerializationRuntime.ScalarShape scalar)
        || scalar.kind() != SerializationRuntime.ScalarKind.STRING) {
      throw new Failure(code("MAP-KEY"), path, format + " mapping key must be String");
    }
  }

  private List<Object> sequenceValues(
      SerializationRuntime.SequenceShape shape, Object value, String path) {
    try {
      return VALUES.sequence(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private List<StructuredValueAccess.MapEntry> mapValues(
      SerializationRuntime.MapShape shape, Object value, String path) {
    try {
      return VALUES.map(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private StructuredValueAccess.AggregateValue aggregateValue(
      SerializationRuntime.AggregateShape shape, Object value, String path) {
    try {
      return VALUES.aggregate(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private String enumVariant(SerializationRuntime.EnumShape shape, Object value, String path) {
    try {
      return VALUES.enumVariant(shape, value);
    } catch (StructuredValueAccess.ValueAccessException failure) {
      throw typeMismatch(path, failure.expected());
    }
  }

  private String string(JsonParser reader, String path) throws IOException {
    requireToken(reader, JsonToken.VALUE_STRING, path);
    String value = reader.getText();
    requireString(value, path);
    return value;
  }

  private String number(JsonParser reader, String path) throws IOException {
    JsonToken token = reader.currentToken();
    if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
      throw typeMismatch(path, "number");
    }
    return reader.getText();
  }

  private int parseInteger(String value, String path) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException failure) {
      throw new Failure(code("NUMBER"), path, "Integer is out of range");
    }
  }

  private long parseLong(String value, String path) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException failure) {
      throw new Failure(code("NUMBER"), path, "Long is out of range");
    }
  }

  private float parseFloat(String value, String path) {
    try {
      float number = Float.parseFloat(value);
      if (!Float.isFinite(number)) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException failure) {
      throw new Failure(code("NUMBER"), path, "Float is out of range");
    }
  }

  private double parseDouble(String value, String path) {
    try {
      double number = Double.parseDouble(value);
      if (!Double.isFinite(number)) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException failure) {
      throw new Failure(code("NUMBER"), path, "Double is out of range");
    }
  }

  private void requireToken(JsonParser reader, JsonToken expected, String path) {
    JsonToken actual = reader.currentToken();
    if (actual != expected) {
      throw new Failure(
          code("TYPE"),
          path,
          "expected "
              + expected.name().toLowerCase()
              + " but found "
              + (actual == null ? "end of input" : actual.name().toLowerCase()));
    }
  }

  private void rejectNativeMetadata(JsonParser reader, String path) throws IOException {
    if (unsupportedToken.test(reader)
        || (reader.canReadObjectId() && reader.getObjectId() != null)
        || (reader.canReadTypeId() && reader.getTypeId() != null)) {
      throw new Failure(
          code("UNSUPPORTED-FEATURE"), path, "YAML anchors and tags are not supported");
    }
  }

  private void requireDepth(String path, int depth) {
    if (depth > MAX_DEPTH) {
      throw new Failure(code("LIMIT"), path, format + " nesting exceeds the depth limit");
    }
  }

  private void requireElements(int count, String path) {
    if (count > MAX_ELEMENTS) {
      throw new Failure(code("LIMIT"), path, format + " collection exceeds the element limit");
    }
  }

  private void requireString(String value, String path) {
    if (value.length() > MAX_STRING_LENGTH) {
      throw new Failure(code("LIMIT"), path, format + " string exceeds the length limit");
    }
  }

  private Failure typeMismatch(String path, String expected) {
    return new Failure(code("TYPE"), path, "expected " + expected);
  }

  private String code(String suffix) {
    return codePrefix + "-" + suffix;
  }

  private NormThrownException failure(Failure failure, ExecutionState execution, Node location) {
    return failures.create(
        failure.code,
        failure.getMessage(),
        failure.path,
        failure.offset,
        failure.line,
        failure.column,
        execution,
        location);
  }

  private NormThrownException failure(
      String code,
      String message,
      String path,
      Location position,
      ExecutionState execution,
      Node location) {
    return failures.create(
        code, message, path, position.offset, position.line, position.column, execution, location);
  }

  private static String field(String path, String name) {
    return path + "." + name;
  }

  private static String index(String path, int index) {
    return path + "[" + index + "]";
  }

  private static String message(Exception failure, String fallback) {
    return failure.getMessage() == null ? fallback : failure.getMessage();
  }

  private static Location location(IOException failure) {
    if (failure instanceof JsonProcessingException processing) {
      return location(processing.getLocation());
    }
    return Location.START;
  }

  private static Location location(JsonLocation position) {
    if (position == null) return Location.START;
    long measured = position.getByteOffset();
    if (measured < 0) measured = position.getCharOffset();
    int offset = measured < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, measured);
    return new Location(offset, position.getLineNr(), position.getColumnNr());
  }

  @FunctionalInterface
  interface FailureFactory {
    NormThrownException create(
        String code,
        String message,
        String path,
        int offset,
        int line,
        int column,
        ExecutionState execution,
        Node location);
  }

  private record Location(int offset, int line, int column) {
    private static final Location START = new Location(0, 1, 1);
  }

  private static final class Failure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String path;
    private final int offset;
    private final int line;
    private final int column;

    Failure(String code, String path, String message) {
      this(code, path, message, 0, 1, 1);
    }

    Failure(String code, String path, String message, int offset, int line, int column) {
      super(message);
      this.code = code;
      this.path = path;
      this.offset = offset;
      this.line = line;
      this.column = column;
    }
  }
}
