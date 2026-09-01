package dev.w0fv1.norm.jvm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

public final class JavaApiReportWriter {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  public String write(JarApiSchema schema) {
    StringWriter output = new StringWriter();
    try {
      write(schema, output);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return output.toString();
  }

  public void write(JarApiSchema schema, Writer output) throws IOException {
    JsonWriter writer = new JsonWriter(output);
    writer.setIndent("  ");
    writer.beginObject();
    writer.name("formatVersion").value(1);
    writer.name("apiId").value(schema.apiId().value());
    writer.name("summary");
    JSON.toJson(summary(schema), writer);
    writer.name("types").beginArray();
    for (JavaApiType type : schema.types()) {
      JSON.toJson(type(type), writer);
    }
    writer.endArray();
    writer.endObject();
    writer.flush();
    output.write('\n');
  }

  private static JsonObject summary(JarApiSchema schema) {
    long stableTypes =
        schema.types().stream()
            .filter(type -> type.disposition() != JavaApiDisposition.EXCLUDED_DEPRECATED)
            .count();
    JsonObject summary = new JsonObject();
    summary.addProperty("stableTypes", stableTypes);
    summary.addProperty(
        "stableFields",
        schema.types().stream()
            .flatMap(type -> type.fields().stream())
            .filter(field -> field.disposition() != JavaApiDisposition.EXCLUDED_DEPRECATED)
            .count());
    summary.addProperty(
        "stableMethods",
        schema.types().stream()
            .flatMap(type -> type.methods().stream())
            .filter(method -> method.disposition() != JavaApiDisposition.EXCLUDED_DEPRECATED)
            .count());
    summary.addProperty(
        "bindableMembers",
        schema.types().stream()
                .flatMap(type -> type.fields().stream())
                .filter(field -> field.disposition() == JavaApiDisposition.BINDABLE)
                .count()
            + schema.types().stream()
                .flatMap(type -> type.methods().stream())
                .filter(method -> method.disposition() == JavaApiDisposition.BINDABLE)
                .count());
    summary.addProperty(
        "unsupportedMembers",
        schema.types().stream()
                .flatMap(type -> type.fields().stream())
                .filter(field -> field.disposition() == JavaApiDisposition.UNSUPPORTED)
                .count()
            + schema.types().stream()
                .flatMap(type -> type.methods().stream())
                .filter(method -> method.disposition() == JavaApiDisposition.UNSUPPORTED)
                .count());
    summary.addProperty(
        "excludedDeprecatedMembers",
        schema.types().stream()
                .flatMap(type -> type.fields().stream())
                .filter(field -> field.disposition() == JavaApiDisposition.EXCLUDED_DEPRECATED)
                .count()
            + schema.types().stream()
                .flatMap(type -> type.methods().stream())
                .filter(method -> method.disposition() == JavaApiDisposition.EXCLUDED_DEPRECATED)
                .count());
    return summary;
  }

  private static JsonObject type(JavaApiType type) {
    JsonObject value = new JsonObject();
    value.addProperty("binaryName", type.binaryName());
    value.addProperty("kind", type.kind().name());
    value.addProperty("modifiers", type.modifiers());
    value.add("signature", classSignature(type.signature()));
    value.add("annotations", annotations(type.annotations()));
    value.add("typeAnnotations", typeAnnotations(type.typeAnnotations()));
    optional(value, "enclosingType", type.enclosingType());
    JsonArray recordComponents = new JsonArray();
    for (JavaApiRecordComponent component : type.recordComponents()) {
      JsonObject item = new JsonObject();
      item.addProperty("name", component.name());
      item.addProperty("descriptor", component.descriptor());
      item.add("type", typeSignature(component.type()));
      item.add("annotations", annotations(component.annotations()));
      item.add("typeAnnotations", typeAnnotations(component.typeAnnotations()));
      recordComponents.add(item);
    }
    value.add("recordComponents", recordComponents);
    value.add("permittedSubclasses", strings(type.permittedSubclasses()));
    value.addProperty("disposition", type.disposition().name());
    JsonArray fields = new JsonArray();
    type.fields().forEach(field -> fields.add(field(field)));
    value.add("fields", fields);
    JsonArray methods = new JsonArray();
    type.methods().forEach(method -> methods.add(method(method)));
    value.add("methods", methods);
    return value;
  }

  private static JsonObject field(JavaApiField field) {
    JsonObject value = member(field.name(), field.descriptor(), field.modifiers());
    value.add("type", typeSignature(field.type()));
    field.constantValue().ifPresent(constant -> value.add("constantValue", constant(constant)));
    value.add("annotations", annotations(field.annotations()));
    value.add("typeAnnotations", typeAnnotations(field.typeAnnotations()));
    value.addProperty("disposition", field.disposition().name());
    field.issue().ifPresent(issue -> value.add("issue", issue(issue)));
    return value;
  }

  private static JsonObject method(JavaApiMethod method) {
    JsonObject value = member(method.name(), method.descriptor(), method.modifiers());
    value.add("signature", methodSignature(method.signature()));
    value.addProperty("kind", method.kind().name());
    value.add("exceptions", strings(method.exceptions()));
    value.add("annotations", annotations(method.annotations()));
    value.add("typeAnnotations", typeAnnotations(method.typeAnnotations()));
    JsonArray parameters = new JsonArray();
    for (JavaApiParameter parameter : method.parameters()) {
      JsonObject item = new JsonObject();
      item.addProperty("index", parameter.index());
      optional(item, "name", parameter.name());
      item.addProperty("modifiers", parameter.modifiers());
      item.add("annotations", annotations(parameter.annotations()));
      parameters.add(item);
    }
    value.add("parameters", parameters);
    method
        .annotationDefault()
        .ifPresent(item -> value.add("annotationDefault", annotationValue(item)));
    value.addProperty("disposition", method.disposition().name());
    method.issue().ifPresent(issue -> value.add("issue", issue(issue)));
    return value;
  }

  private static JsonObject classSignature(JavaClassSignature signature) {
    JsonObject value = new JsonObject();
    value.add("typeParameters", typeParameters(signature.typeParameters()));
    signature.superclass().ifPresent(item -> value.add("superclass", typeSignature(item)));
    JsonArray interfaces = new JsonArray();
    signature.interfaces().forEach(item -> interfaces.add(typeSignature(item)));
    value.add("interfaces", interfaces);
    return value;
  }

  private static JsonObject methodSignature(JavaMethodSignature signature) {
    JsonObject value = new JsonObject();
    value.add("typeParameters", typeParameters(signature.typeParameters()));
    JsonArray parameters = new JsonArray();
    signature.parameters().forEach(item -> parameters.add(typeSignature(item)));
    value.add("parameters", parameters);
    value.add("returnType", typeSignature(signature.returnType()));
    JsonArray exceptions = new JsonArray();
    signature.exceptions().forEach(item -> exceptions.add(typeSignature(item)));
    value.add("exceptions", exceptions);
    return value;
  }

  private static JsonArray typeParameters(List<JavaTypeParameter> parameters) {
    JsonArray values = new JsonArray();
    for (JavaTypeParameter parameter : parameters) {
      JsonObject value = new JsonObject();
      value.addProperty("name", parameter.name());
      parameter.classBound().ifPresent(item -> value.add("classBound", typeSignature(item)));
      JsonArray interfaceBounds = new JsonArray();
      parameter.interfaceBounds().forEach(item -> interfaceBounds.add(typeSignature(item)));
      value.add("interfaceBounds", interfaceBounds);
      values.add(value);
    }
    return values;
  }

  private static JsonObject typeSignature(JavaTypeSignature signature) {
    JsonObject value = new JsonObject();
    switch (signature) {
      case JavaPrimitiveTypeSignature primitive -> {
        value.addProperty("kind", "primitive");
        value.addProperty("name", primitive.type().displayName());
      }
      case JavaTypeVariableSignature variable -> {
        value.addProperty("kind", "variable");
        value.addProperty("name", variable.name());
      }
      case JavaArrayTypeSignature array -> {
        value.addProperty("kind", "array");
        value.add("component", typeSignature(array.component()));
      }
      case JavaClassTypeSignature classType -> {
        value.addProperty("kind", "class");
        value.addProperty("binaryName", classType.binaryName());
        JsonArray segments = new JsonArray();
        for (JavaClassTypeSegment segment : classType.segments()) {
          JsonObject segmentValue = new JsonObject();
          segmentValue.addProperty("name", segment.name());
          JsonArray arguments = new JsonArray();
          for (JavaTypeArgument argument : segment.arguments()) {
            JsonObject argumentValue = new JsonObject();
            argumentValue.addProperty("variance", argument.variance().name());
            argument.type().ifPresent(item -> argumentValue.add("type", typeSignature(item)));
            arguments.add(argumentValue);
          }
          segmentValue.add("arguments", arguments);
          segments.add(segmentValue);
        }
        value.add("segments", segments);
      }
    }
    return value;
  }

  private static JsonObject member(String name, String descriptor, int modifiers) {
    JsonObject value = new JsonObject();
    value.addProperty("name", name);
    value.addProperty("descriptor", descriptor);
    value.addProperty("modifiers", modifiers);
    return value;
  }

  private static JsonObject issue(JavaApiIssue issue) {
    JsonObject value = new JsonObject();
    value.addProperty("code", issue.code().name());
    value.addProperty("detail", issue.detail());
    return value;
  }

  private static JsonArray annotations(List<JavaApiAnnotation> annotations) {
    JsonArray values = new JsonArray();
    for (JavaApiAnnotation annotation : annotations) {
      JsonObject value = new JsonObject();
      value.addProperty("type", annotation.type());
      value.addProperty("runtimeVisible", annotation.runtimeVisible());
      JsonObject elements = new JsonObject();
      annotation
          .elements()
          .forEach(element -> elements.add(element.name(), annotationValue(element.value())));
      value.add("elements", elements);
      values.add(value);
    }
    return values;
  }

  private static JsonArray typeAnnotations(List<JavaApiTypeAnnotation> typeAnnotations) {
    JsonArray values = new JsonArray();
    for (JavaApiTypeAnnotation typeAnnotation : typeAnnotations) {
      JsonObject value = new JsonObject();
      value.addProperty("typeReference", typeAnnotation.typeReference());
      optional(value, "typePath", typeAnnotation.typePath());
      value.add("annotation", annotations(List.of(typeAnnotation.annotation())).get(0));
      values.add(value);
    }
    return values;
  }

  private static JsonElement annotationValue(JavaAnnotationValue value) {
    return switch (value) {
      case JavaAnnotationConstantValue constant -> constant(constant.value());
      case JavaAnnotationClassValue classValue -> {
        JsonObject item = new JsonObject();
        item.addProperty("class", classValue.descriptor());
        yield item;
      }
      case JavaAnnotationEnumValue enumValue -> {
        JsonObject item = new JsonObject();
        item.addProperty("enumType", enumValue.type());
        item.addProperty("constant", enumValue.constant());
        yield item;
      }
      case JavaAnnotationNestedValue nested -> annotations(List.of(nested.annotation())).get(0);
      case JavaAnnotationArrayValue array -> {
        JsonArray items = new JsonArray();
        array.values().forEach(item -> items.add(annotationValue(item)));
        yield items;
      }
    };
  }

  private static JsonElement constant(Object value) {
    return switch (value) {
      case Boolean item -> new JsonPrimitive(item);
      case Character item -> new JsonPrimitive(item);
      case Number item -> new JsonPrimitive(item);
      case String item -> new JsonPrimitive(item);
      default -> throw new IllegalArgumentException("unsupported constant " + value.getClass());
    };
  }

  private static JsonArray strings(List<String> values) {
    JsonArray array = new JsonArray();
    values.forEach(array::add);
    return array;
  }

  private static void optional(JsonObject object, String name, Optional<String> value) {
    value.ifPresent(item -> object.addProperty(name, item));
  }
}
