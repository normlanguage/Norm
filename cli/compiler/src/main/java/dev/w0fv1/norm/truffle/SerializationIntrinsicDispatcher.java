package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.Map;

final class SerializationIntrinsicDispatcher {
  private SerializationIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case JSON_SCHEMA, JSON_FUNCTION_SCHEMA, JSON_FUNCTION_INVOKE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              if (intrinsic == IntrinsicId.JSON_SCHEMA) {
                var schema = new JsonSchemaRuntime();
                return schema.document(
                    schema.schema(
                        annotations
                            .serialization()
                            .shape(((RuntimeValues.ClassValue) arguments[0]).reflectedType())));
              }
              var operation = (RuntimeValues.Closure) arguments[0];
              return intrinsic == IntrinsicId.JSON_FUNCTION_SCHEMA
                  ? JsonFunctionRuntime.schema(operation, annotations)
                  : JsonFunctionRuntime.invoke(
                      operation, (String) arguments[1], annotations, execution, location);
            } catch (SerializationRuntime.ShapeException failure) {
              throw JsonRuntime.shapeFailure(failure, execution, location);
            } catch (IllegalArgumentException failure) {
              throw execution
                  .values()
                  .jsonException(
                      "NORM-JSON-FUNCTION",
                      failure.getMessage(),
                      "$",
                      0,
                      1,
                      1,
                      execution,
                      location);
            }
          };
      case JSON_ENCODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (annotations == null || execution == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
            return annotations
                .mapper()
                .write(
                    JsonDataFormat.INSTANCE, reflected.reflectedType(), first, execution, location);
          };
      case JSON_DECODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || execution == null || type == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            return annotations
                .mapper()
                .read(JsonDataFormat.INSTANCE, type, (String) first, execution, location);
          };
      case JSON_PARSE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || execution == null || type == null) {
              throw new IllegalStateException("JSON runtime is unavailable");
            }
            return JsonRuntime.parseValue((String) first, type, annotations, execution, location);
          };
      case JSON_WRITE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || execution == null) {
              throw new IllegalStateException("JSON runtime is unavailable");
            }
            return JsonRuntime.writeValue(first, annotations, execution, location);
          };
      case XML_ENCODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (annotations == null || execution == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
            return annotations
                .mapper()
                .write(annotations.xml(), reflected.reflectedType(), first, execution, location);
          };
      case XML_DECODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || execution == null || type == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            return annotations
                .mapper()
                .read(annotations.xml(), type, (String) first, execution, location);
          };
      case YAML_ENCODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (annotations == null || execution == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
            return annotations
                .mapper()
                .write(
                    YamlDataFormat.INSTANCE, reflected.reflectedType(), first, execution, location);
          };
      case YAML_DECODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || execution == null || type == null) {
              throw new IllegalStateException("serialization runtime is unavailable");
            }
            return annotations
                .mapper()
                .read(YamlDataFormat.INSTANCE, type, (String) first, execution, location);
          };
      case CONFIGURATION_PROPERTIES ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (annotations == null || execution == null || type == null) {
              throw new IllegalStateException("configuration runtime is unavailable");
            }
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) second;
            try {
              Map<String, Object> properties =
                  annotations.configuration().properties(reflected.reflectedType(), first);
              return execution.values().opaque(type, properties, "MutableMap");
            } catch (SerializationRuntime.ShapeException | IllegalArgumentException failure) {
              throw new NormGuestException(
                  RuntimeErrorCode.INVALID_ARGUMENT, failure.getMessage(), location);
            }
          };
      default ->
          throw new IllegalArgumentException("Unsupported serialization intrinsic: " + intrinsic);
    };
  }
}
