package dev.w0fv1.norm.truffle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.JsonAbi;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class JsonRuntime {
  private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
  private static final int MAX_STRING_LENGTH = 1024 * 1024;
  private static final int MAX_ELEMENTS = 1_000_000;
  private static final int MAX_DEPTH = 128;
  private static final JsonFactory FACTORY =
      JsonFactory.builder()
          .streamReadConstraints(
              StreamReadConstraints.builder()
                  .maxDocumentLength(MAX_INPUT_BYTES)
                  .maxNestingDepth(MAX_DEPTH)
                  .maxStringLength(MAX_STRING_LENGTH)
                  .maxNameLength(MAX_STRING_LENGTH)
                  .build())
          .streamWriteConstraints(
              StreamWriteConstraints.builder().maxNestingDepth(MAX_DEPTH).build())
          .build();
  private static final JacksonDataRuntime DATA =
      new JacksonDataRuntime(
          "JSON",
          FACTORY,
          reader -> false,
          (code, message, path, offset, line, column, execution, location) ->
              execution
                  .values()
                  .jsonException(code, message, path, offset, line, column, execution, location));

  private JsonRuntime() {}

  static String encode(
      Object value, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    return DATA.encode(value, shape, execution, location);
  }

  static Object decode(
      String input, SerializationRuntime.Shape shape, ExecutionState execution, Node location) {
    return DATA.decode(input, shape, execution, location);
  }

  static NormThrownException shapeFailure(
      SerializationRuntime.ShapeException failure, ExecutionState execution, Node location) {
    return DATA.shapeFailure(failure, execution, location);
  }

  static Object parseValue(
      String input,
      CoreType type,
      AnnotationRuntime reflection,
      ExecutionState execution,
      Node location) {
    if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
      throw jsonFailure(
          "NORM-JSON-LIMIT",
          "JSON input exceeds the byte limit",
          "$",
          0,
          1,
          1,
          execution,
          location);
    }
    try {
      TreeType tree = treeType(type, reflection);
      JsonParser reader = FACTORY.createParser(input.getBytes(StandardCharsets.UTF_8));
      reader.nextToken();
      Object result = readValue(reader, tree, "$", 0);
      if (reader.nextToken() != null) {
        throw new JsonFailure("NORM-JSON-SYNTAX", "$", "trailing JSON content");
      }
      reader.close();
      return result;
    } catch (JsonFailure failure) {
      throw jsonFailure(
          failure.code,
          failure.getMessage(),
          failure.path,
          failure.offset,
          failure.line,
          failure.column,
          execution,
          location);
    } catch (StreamConstraintsException failure) {
      Location parsed = location(failure.getLocation());
      throw jsonFailure(
          "NORM-JSON-LIMIT",
          failure.getMessage(),
          "$",
          parsed.offset,
          parsed.line,
          parsed.column,
          execution,
          location);
    } catch (IOException failure) {
      Location parsed = location(failure);
      throw jsonFailure(
          "NORM-JSON-SYNTAX",
          failure.getMessage() == null ? "invalid JSON" : failure.getMessage(),
          "$",
          parsed.offset,
          parsed.line,
          parsed.column,
          execution,
          location);
    }
  }

  static String writeValue(
      Object value, AnnotationRuntime reflection, ExecutionState execution, Node location) {
    try {
      if (!(value instanceof RuntimeValues.EnumValue treeValue)) {
        throw new JsonFailure("NORM-JSON-TYPE", "$", "expected JsonValue");
      }
      TreeType tree = treeType(treeValue.type(), reflection);
      StringWriter output = new StringWriter();
      JsonGenerator writer = FACTORY.createGenerator(output);
      writeValue(writer, tree, treeValue, "$", 0);
      writer.close();
      return output.toString();
    } catch (JsonFailure failure) {
      throw jsonFailure(
          failure.code,
          failure.getMessage(),
          failure.path,
          failure.offset,
          failure.line,
          failure.column,
          execution,
          location);
    } catch (IOException failure) {
      throw jsonFailure("NORM-JSON-WRITE", failure.getMessage(), "$", 0, 1, 1, execution, location);
    }
  }

  private static Object readValue(JsonParser reader, TreeType tree, String path, int depth)
      throws IOException {
    requireDepth(path, depth);
    if (reader.currentToken() == null) {
      throw new JsonFailure("NORM-JSON-SYNTAX", path, "expected a JSON value");
    }
    return switch (reader.currentToken()) {
      case START_OBJECT -> {
        CoreEnumVariant variant = tree.variant(JsonAbi.VALUE_VARIANT_OBJECT);
        CoreType mapType = tree.payloadType(variant);
        RuntimeValues.MapValue fields = new RuntimeValues.MapValue(mapType);
        int count = 0;
        while (reader.nextToken() != JsonToken.END_OBJECT) {
          requireElements(++count, path);
          requireToken(reader, JsonToken.FIELD_NAME, path);
          String name = reader.currentName();
          if (RuntimeValues.mapContains(fields, name)) {
            throw new JsonFailure(
                "NORM-JSON-DUPLICATE-KEY", field(path, name), "duplicate JSON object key");
          }
          reader.nextToken();
          RuntimeValues.mapPut(fields, name, readValue(reader, tree, field(path, name), depth + 1));
        }
        yield tree.value(variant, fields);
      }
      case START_ARRAY -> {
        CoreEnumVariant variant = tree.variant(JsonAbi.VALUE_VARIANT_ARRAY);
        CoreType listType = tree.payloadType(variant);
        RuntimeValues.ListValue values = new RuntimeValues.ListValue(listType);
        while (reader.nextToken() != JsonToken.END_ARRAY) {
          requireElements(values.values.size() + 1, path);
          values.values.add(readValue(reader, tree, index(path, values.values.size()), depth + 1));
        }
        yield tree.value(variant, values);
      }
      case VALUE_STRING ->
          tree.value(tree.variant(JsonAbi.VALUE_VARIANT_STRING), string(reader, path));
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT ->
          tree.value(tree.variant(JsonAbi.VALUE_VARIANT_NUMBER), number(reader, path));
      case VALUE_TRUE, VALUE_FALSE -> {
        boolean value = reader.getBooleanValue();
        yield tree.value(tree.variant(JsonAbi.VALUE_VARIANT_BOOLEAN), value);
      }
      case VALUE_NULL -> {
        yield tree.value(tree.variant(JsonAbi.VALUE_VARIANT_NULL));
      }
      default -> throw new JsonFailure("NORM-JSON-SYNTAX", path, "expected a JSON value");
    };
  }

  private static void writeValue(
      JsonGenerator writer, TreeType tree, RuntimeValues.EnumValue value, String path, int depth)
      throws IOException {
    requireDepth(path, depth);
    if (!value.definition().equals(tree.definition)) {
      throw new JsonFailure("NORM-JSON-TYPE", path, "expected JsonValue");
    }
    switch (value.variantKey()) {
      case JsonAbi.VALUE_VARIANT_OBJECT -> {
        RuntimeValues.MapValue fields = (RuntimeValues.MapValue) value.field(0);
        requireElements(fields.values.size(), path);
        writer.writeStartObject();
        for (var entry : fields.values.entrySet()) {
          if (!(entry.getKey().value instanceof String name)
              || !(entry.getValue() instanceof RuntimeValues.EnumValue child)) {
            throw new JsonFailure("NORM-JSON-TYPE", path, "invalid JsonValue.Object payload");
          }
          writer.writeFieldName(name);
          writeValue(writer, tree, child, field(path, name), depth + 1);
        }
        writer.writeEndObject();
      }
      case JsonAbi.VALUE_VARIANT_ARRAY -> {
        RuntimeValues.ListValue values = (RuntimeValues.ListValue) value.field(0);
        requireElements(values.values.size(), path);
        writer.writeStartArray();
        for (int index = 0; index < values.values.size(); index++) {
          if (!(values.values.get(index) instanceof RuntimeValues.EnumValue child)) {
            throw new JsonFailure("NORM-JSON-TYPE", path, "invalid JsonValue.Array payload");
          }
          writeValue(writer, tree, child, index(path, index), depth + 1);
        }
        writer.writeEndArray();
      }
      case JsonAbi.VALUE_VARIANT_STRING -> writer.writeString((String) value.field(0));
      case JsonAbi.VALUE_VARIANT_NUMBER -> {
        String number = (String) value.field(0);
        validateNumber(number, path);
        writer.writeNumber(number);
      }
      case JsonAbi.VALUE_VARIANT_BOOLEAN -> writer.writeBoolean((Boolean) value.field(0));
      case JsonAbi.VALUE_VARIANT_NULL -> writer.writeNull();
      default -> throw new JsonFailure("NORM-JSON-TYPE", path, "unknown JsonValue variant");
    }
  }

  private static TreeType treeType(CoreType type, AnnotationRuntime reflection) {
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference.External external)
        || !(reflection.program().definition(external.definition()).orElse(null)
            instanceof CoreDefinition.Enum enumeration)
        || !enumeration.nominalType().module().name().equals(JsonAbi.MODULE_NAME)
        || enumeration.nominalType().module().version() != JsonAbi.MODULE_VERSION
        || !enumeration.nominalType().packageName().equals(JsonAbi.PACKAGE_NAME)
        || !enumeration.nominalType().name().equals(JsonAbi.VALUE_TYPE_NAME)) {
      throw new JsonFailure(
          "NORM-JSON-TYPE",
          "$",
          "expected " + JsonAbi.PACKAGE_NAME + "." + JsonAbi.VALUE_TYPE_NAME);
    }
    return new TreeType(type, external.definition(), enumeration, reflection);
  }

  private static void validateNumber(String value, String path) {
    try (JsonParser reader = FACTORY.createParser(value)) {
      JsonToken token = reader.nextToken();
      if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
        throw new IOException();
      }
      if (reader.nextToken() != null) throw new IOException();
    } catch (IOException failure) {
      throw new JsonFailure("NORM-JSON-NUMBER", path, "invalid JSON number");
    }
  }

  private static String string(JsonParser reader, String path) throws IOException {
    requireToken(reader, JsonToken.VALUE_STRING, path);
    String value = reader.getText();
    requireString(value, path);
    return value;
  }

  private static String number(JsonParser reader, String path) throws IOException {
    JsonToken token = reader.currentToken();
    if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
      throw typeMismatch(path, "number");
    }
    return reader.getText();
  }

  private static void requireToken(JsonParser reader, JsonToken expected, String path) {
    JsonToken actual = reader.currentToken();
    if (actual != expected) {
      throw new JsonFailure(
          "NORM-JSON-TYPE",
          path,
          "expected "
              + expected.name().toLowerCase()
              + " but found "
              + (actual == null ? "end of input" : actual.name().toLowerCase()));
    }
  }

  private static void requireDepth(String path, int depth) {
    if (depth > MAX_DEPTH) {
      throw new JsonFailure("NORM-JSON-LIMIT", path, "JSON nesting exceeds the depth limit");
    }
  }

  private static void requireElements(int count, String path) {
    if (count > MAX_ELEMENTS) {
      throw new JsonFailure("NORM-JSON-LIMIT", path, "JSON collection exceeds the element limit");
    }
  }

  private static void requireString(String value, String path) {
    if (value.length() > MAX_STRING_LENGTH) {
      throw new JsonFailure("NORM-JSON-LIMIT", path, "JSON string exceeds the length limit");
    }
  }

  private static JsonFailure typeMismatch(String path, String expected) {
    return new JsonFailure("NORM-JSON-TYPE", path, "expected " + expected);
  }

  private static String field(String path, String name) {
    return path + "." + name;
  }

  private static String index(String path, int index) {
    return path + "[" + index + "]";
  }

  private static NormThrownException jsonFailure(
      String code,
      String message,
      String path,
      int offset,
      int line,
      int column,
      ExecutionState execution,
      Node location) {
    return execution
        .values()
        .jsonException(code, message, path, offset, line, column, execution, location);
  }

  private static Location location(IOException failure) {
    if (failure instanceof JsonProcessingException processing) {
      return location(processing.getLocation());
    }
    return new Location("$", 0, 1, 1);
  }

  private static Location location(JsonLocation position) {
    if (position == null) return new Location("$", 0, 1, 1);
    long measured = position.getByteOffset();
    if (measured < 0) measured = position.getCharOffset();
    int offset = measured < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, measured);
    return new Location("$", offset, position.getLineNr(), position.getColumnNr());
  }

  private record Location(String path, int offset, int line, int column) {}

  private record TreeType(
      CoreType type,
      DefinitionId definition,
      CoreDefinition.Enum declaration,
      AnnotationRuntime reflection) {
    CoreEnumVariant variant(String key) {
      return declaration.variants().stream()
          .filter(candidate -> candidate.key().equals(key))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("JsonValue variant is absent: " + key));
    }

    CoreType payloadType(CoreEnumVariant variant) {
      if (variant.fields().size() != 1) {
        throw new IllegalStateException("JsonValue variant payload is invalid: " + variant.key());
      }
      return CoreTypes.absolute(
          variant.fields().getFirst().type(), definition, reflection.program());
    }

    RuntimeValues.EnumValue value(CoreEnumVariant variant, Object... payload) {
      return new RuntimeValues.EnumValue(
          definition, type, declaration.nominalType().name(), variant.key(), List.of(payload));
    }
  }

  private static final class JsonFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String path;
    private final int offset;
    private final int line;
    private final int column;

    JsonFailure(String code, String path, String message) {
      this(code, path, message, 0, 1, 1);
    }

    JsonFailure(String code, String path, String message, int offset, int line, int column) {
      super(message);
      this.code = code;
      this.path = path;
      this.offset = offset;
      this.line = line;
      this.column = column;
    }
  }
}
