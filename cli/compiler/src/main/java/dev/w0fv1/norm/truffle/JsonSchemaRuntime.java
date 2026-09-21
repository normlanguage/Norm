package dev.w0fv1.norm.truffle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import dev.w0fv1.norm.core.CoreType;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonSchemaRuntime {
  private final Map<CoreType, String> names = new LinkedHashMap<>();
  private final Map<String, Object> definitions = new LinkedHashMap<String, Object>();

  Map<String, Object> schema(SerializationRuntime.Shape source) {
    var shape = SerializationRuntime.resolved(source);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    switch (shape) {
      case SerializationRuntime.ScalarShape scalar -> {
        result.put(
            "type",
            switch (scalar.kind()) {
              case STRING, CODE_POINT -> "string";
              case BOOLEAN -> "boolean";
              case INTEGER, LONG -> "integer";
              case FLOAT, DOUBLE -> "number";
            });
        if (scalar.kind() == SerializationRuntime.ScalarKind.INTEGER) {
          result.put("minimum", Integer.MIN_VALUE);
          result.put("maximum", Integer.MAX_VALUE);
        }
        if (scalar.kind() == SerializationRuntime.ScalarKind.CODE_POINT) {
          result.put("minLength", 1);
          result.put("maxLength", 1);
        }
      }
      case SerializationRuntime.NullableShape nullable -> {
        var alternatives = new ArrayList<Object>();
        alternatives.add(schema(nullable.value()));
        var nullType = new LinkedHashMap<String, Object>();
        nullType.put("type", "null");
        alternatives.add(nullType);
        result.put("anyOf", alternatives);
      }
      case SerializationRuntime.SequenceShape sequence -> {
        result.put("type", "array");
        result.put("items", schema(sequence.element()));
      }
      case SerializationRuntime.MapShape map -> {
        if (!(SerializationRuntime.resolved(map.key())
                instanceof SerializationRuntime.ScalarShape key)
            || key.kind() != SerializationRuntime.ScalarKind.STRING)
          throw new SerializationRuntime.ShapeException(
              "NORM-SERIALIZATION-UNSUPPORTED", "$", "JSON schemas require string map keys");
        result.put("type", "object");
        result.put("additionalProperties", schema(map.value()));
      }
      case SerializationRuntime.EnumShape enumeration -> {
        result.put("type", "string");
        var values = new ArrayList<Object>();
        enumeration.declaration().variants().forEach(variant -> values.add(variant.key()));
        result.put("enum", values);
      }
      case SerializationRuntime.AggregateShape aggregate -> {
        String name = names.get(shape.type());
        if (name == null) {
          name = "type" + names.size();
          names.put(shape.type(), name);
          var definition = new LinkedHashMap<String, Object>();
          definitions.put(name, definition);
          var properties = new LinkedHashMap<String, Object>();
          var required = new ArrayList<Object>();
          for (var field : aggregate.fields()) {
            if (field.ignored()) continue;
            properties.put(field.name(), schema(field.shape()));
            if (!(SerializationRuntime.resolved(field.shape())
                instanceof SerializationRuntime.NullableShape)) required.add(field.name());
          }
          definition.put("type", "object");
          definition.put("properties", properties);
          definition.put("required", required);
          definition.put("additionalProperties", false);
        }
        result.put("$ref", "#/$defs/" + name);
      }
      case SerializationRuntime.DeferredShape ignored -> throw new IllegalStateException();
    }
    return result;
  }

  private static void write(JsonGenerator writer, Object value) throws IOException {
    if (value instanceof Map<?, ?> fields) {
      writer.writeStartObject();
      for (var field : fields.entrySet()) {
        writer.writeFieldName((String) field.getKey());
        write(writer, field.getValue());
      }
      writer.writeEndObject();
    } else if (value instanceof List<?> items) {
      writer.writeStartArray();
      for (Object item : items) write(writer, item);
      writer.writeEndArray();
    } else if (value instanceof String text) writer.writeString(text);
    else if (value instanceof Boolean flag) writer.writeBoolean(flag);
    else if (value instanceof Integer number) writer.writeNumber(number);
    else throw new IllegalArgumentException("Unsupported schema value");
  }

  String document(Map<String, Object> root) {
    if (!definitions.isEmpty()) root.put("$defs", definitions);
    var text = new StringWriter();
    try (var writer = new JsonFactory().createGenerator(text)) {
      write(writer, root);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    return text.toString();
  }
}
