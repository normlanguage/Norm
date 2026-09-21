package dev.w0fv1.norm.truffle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypes;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

final class JsonFunctionRuntime {
  private JsonFunctionRuntime() {}

  static String schema(RuntimeValues.Closure operation, AnnotationRuntime reflection) {
    if (operation.unbound())
      throw new IllegalArgumentException("JSON invocation requires a bound receiver");
    var callable = reflection.callable(operation);
    var schema = new JsonSchemaRuntime();
    var root = new LinkedHashMap<String, Object>();
    var properties = new LinkedHashMap<String, Object>();
    var required = new ArrayList<Object>();
    for (int index = 0; index < callable.parameters().size(); index++) {
      var parameter = callable.parameters().get(index);
      var shape = reflection.serialization().shape(type(operation, parameter.type(), reflection));
      properties.put(parameter.name(), schema.schema(shape));
      if (reflection.parameterDefault(operation, index).isEmpty()
          && !(SerializationRuntime.resolved(shape) instanceof SerializationRuntime.NullableShape))
        required.add(parameter.name());
    }
    var result = type(operation, callable.returnType(), reflection);
    if (!result.equals(CoreType.VOID)) reflection.serialization().shape(result);
    root.put("type", "object");
    root.put("properties", properties);
    root.put("required", required);
    root.put("additionalProperties", false);
    return schema.document(root);
  }

  static String invoke(
      RuntimeValues.Closure operation,
      String input,
      AnnotationRuntime reflection,
      ExecutionState execution,
      Node location) {
    schema(operation, reflection);
    if (input.length() > JacksonDataRuntime.MAX_INPUT_BYTES)
      throw new IllegalArgumentException("JSON arguments exceed the size limit");
    var factory =
        JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(
                StreamReadConstraints.builder()
                    .maxNestingDepth(JacksonDataRuntime.MAX_DEPTH)
                    .maxStringLength(JacksonDataRuntime.MAX_STRING_LENGTH)
                    .maxDocumentLength(JacksonDataRuntime.MAX_INPUT_BYTES)
                    .build())
            .build();
    var fields = new LinkedHashMap<String, String>();
    try (var reader = factory.createParser(input)) {
      if (reader.nextToken() != JsonToken.START_OBJECT)
        throw new IllegalArgumentException("JSON arguments must be an object");
      while (reader.nextToken() != JsonToken.END_OBJECT) {
        if (reader.currentToken() != JsonToken.FIELD_NAME)
          throw new IllegalArgumentException("Invalid JSON argument object");
        String name = reader.currentName();
        reader.nextToken();
        var text = new StringWriter();
        try (var writer = factory.createGenerator(text)) {
          writer.copyCurrentStructure(reader);
        }
        fields.put(name, text.toString());
      }
      if (reader.nextToken() != null)
        throw new IllegalArgumentException("Trailing JSON argument data");
    } catch (IOException failure) {
      throw new IllegalArgumentException(failure.getMessage(), failure);
    }
    var callable = reflection.callable(operation);
    for (String name : fields.keySet()) {
      if (callable.parameters().stream().noneMatch(parameter -> parameter.name().equals(name)))
        throw new IllegalArgumentException("Unknown argument: " + name);
    }
    Object[] arguments = new Object[callable.parameters().size()];
    for (int index = 0; index < arguments.length; index++) {
      var parameter = callable.parameters().get(index);
      var parameterType = type(operation, parameter.type(), reflection);
      if (!fields.containsKey(parameter.name())) {
        if (reflection.parameterDefault(operation, index).isPresent()) continue;
        if (!parameterType.isNullable())
          throw new IllegalArgumentException("Missing argument: " + parameter.name());
      }
      arguments[index] =
          reflection
              .mapper()
              .read(
                  JsonDataFormat.INSTANCE,
                  parameterType,
                  fields.getOrDefault(parameter.name(), "null"),
                  execution,
                  location);
    }
    for (int index = 0; index < arguments.length; index++) {
      if (!fields.containsKey(callable.parameters().get(index).name())
          && reflection.parameterDefault(operation, index).isPresent())
        arguments[index] = reflection.defaultArgument(operation, index, execution);
    }
    Object result = RuntimeInvocation.invoke(execution, operation, arguments);
    CoreType resultType = type(operation, callable.returnType(), reflection);
    return resultType.equals(CoreType.VOID)
        ? "null"
        : reflection
            .mapper()
            .write(JsonDataFormat.INSTANCE, resultType, result, execution, location);
  }

  private static CoreType type(
      RuntimeValues.Closure operation, CoreType type, AnnotationRuntime reflection) {
    return CoreTypes.absolute(type, operation.declaration().representative(), reflection.program())
        .substitute(index -> reflection.callableTypeArgument(operation, index));
  }
}
