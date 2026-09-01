package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record JarApiSchema(List<JavaApiType> types, Sha256Digest apiId) {
  public JarApiSchema(List<JavaApiType> types) {
    this(sorted(types), identify(types));
  }

  public JarApiSchema {
    types = List.copyOf(types);
    Objects.requireNonNull(apiId, "apiId");
  }

  private static List<JavaApiType> sorted(List<JavaApiType> types) {
    return types.stream().sorted(Comparator.comparing(JavaApiType::binaryName)).toList();
  }

  private static Sha256Digest identify(List<JavaApiType> types) {
    StringBuilder canonical = new StringBuilder("java-api-v2\n");
    for (JavaApiType type : sorted(types)) {
      append(canonical, "type", type.binaryName(), type.kind(), type.modifiers());
      Map<String, String> typeVariables = classSignature(canonical, type.signature());
      type.annotations().forEach(value -> annotation(canonical, value));
      type.typeAnnotations().forEach(value -> typeAnnotation(canonical, value));
      appendOptional(canonical, "enclosing-type", type.enclosingType());
      for (JavaApiRecordComponent component : type.recordComponents()) {
        append(canonical, "record-component", component.name(), component.descriptor());
        typeSignature(canonical, "component-type", component.type(), typeVariables);
        component.annotations().forEach(value -> annotation(canonical, value));
        component.typeAnnotations().forEach(value -> typeAnnotation(canonical, value));
      }
      type.permittedSubclasses().forEach(value -> append(canonical, "permitted-subclass", value));
      for (JavaApiField field : type.fields()) {
        append(
            canonical,
            "field",
            field.name(),
            field.descriptor(),
            field.modifiers(),
            field.constantValue().map(JarApiSchema::constant).orElse(""));
        typeSignature(canonical, "field-type", field.type(), typeVariables);
        field.annotations().forEach(value -> annotation(canonical, value));
        field.typeAnnotations().forEach(value -> typeAnnotation(canonical, value));
      }
      for (JavaApiMethod method : type.methods()) {
        append(
            canonical,
            "method",
            method.name(),
            method.descriptor(),
            method.modifiers(),
            method.kind());
        methodSignature(canonical, method.signature(), typeVariables);
        method.exceptions().forEach(value -> append(canonical, "exception", value));
        method.annotations().forEach(value -> annotation(canonical, value));
        method.typeAnnotations().forEach(value -> typeAnnotation(canonical, value));
        for (JavaApiParameter parameter : method.parameters()) {
          append(
              canonical,
              "parameter",
              parameter.index(),
              parameter.name().orElse(""),
              parameter.modifiers());
          parameter.annotations().forEach(value -> annotation(canonical, value));
        }
        method
            .annotationDefault()
            .ifPresent(value -> annotationValue(canonical, "annotation-default", value));
      }
    }
    return Sha256Digest.compute(canonical.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, String> classSignature(
      StringBuilder canonical, JavaClassSignature signature) {
    Map<String, String> variables = typeVariables(signature.typeParameters(), "C", Map.of());
    typeParameters(canonical, signature.typeParameters(), variables);
    signature
        .superclass()
        .ifPresent(value -> typeSignature(canonical, "superclass", value, variables));
    signature
        .interfaces()
        .forEach(value -> typeSignature(canonical, "interface", value, variables));
    return variables;
  }

  private static void methodSignature(
      StringBuilder canonical, JavaMethodSignature signature, Map<String, String> classVariables) {
    Map<String, String> variables = typeVariables(signature.typeParameters(), "M", classVariables);
    typeParameters(canonical, signature.typeParameters(), variables);
    signature
        .parameters()
        .forEach(value -> typeSignature(canonical, "parameter-type", value, variables));
    typeSignature(canonical, "return-type", signature.returnType(), variables);
    signature
        .exceptions()
        .forEach(value -> typeSignature(canonical, "generic-exception", value, variables));
  }

  private static Map<String, String> typeVariables(
      List<JavaTypeParameter> parameters, String prefix, Map<String, String> inherited) {
    Map<String, String> variables = new LinkedHashMap<>(inherited);
    for (int index = 0; index < parameters.size(); index++) {
      variables.put(parameters.get(index).name(), prefix + index);
    }
    return Map.copyOf(variables);
  }

  private static void typeParameters(
      StringBuilder canonical, List<JavaTypeParameter> parameters, Map<String, String> variables) {
    for (JavaTypeParameter parameter : parameters) {
      append(canonical, "type-parameter", variables.get(parameter.name()));
      parameter
          .classBound()
          .ifPresent(value -> typeSignature(canonical, "class-bound", value, variables));
      parameter
          .interfaceBounds()
          .forEach(value -> typeSignature(canonical, "interface-bound", value, variables));
    }
  }

  private static void typeSignature(
      StringBuilder canonical,
      String label,
      JavaTypeSignature signature,
      Map<String, String> variables) {
    switch (signature) {
      case JavaPrimitiveTypeSignature primitive ->
          append(canonical, label, "primitive", primitive.type());
      case JavaTypeVariableSignature variable ->
          append(
              canonical,
              label,
              "variable",
              variables.getOrDefault(variable.name(), variable.name()));
      case JavaArrayTypeSignature array -> {
        append(canonical, label, "array");
        typeSignature(canonical, "component", array.component(), variables);
      }
      case JavaClassTypeSignature classType -> {
        append(canonical, label, "class", classType.binaryName());
        for (JavaClassTypeSegment segment : classType.segments()) {
          append(canonical, "segment", segment.name());
          for (JavaTypeArgument argument : segment.arguments()) {
            append(canonical, "argument", argument.variance());
            argument
                .type()
                .ifPresent(value -> typeSignature(canonical, "argument-type", value, variables));
          }
        }
      }
    }
  }

  private static void annotation(StringBuilder canonical, JavaApiAnnotation annotation) {
    append(canonical, "annotation", annotation.type(), annotation.runtimeVisible());
    annotation
        .elements()
        .forEach(
            element -> annotationValue(canonical, "element:" + element.name(), element.value()));
  }

  private static void typeAnnotation(
      StringBuilder canonical, JavaApiTypeAnnotation typeAnnotation) {
    append(
        canonical,
        "type-annotation",
        typeAnnotation.typeReference(),
        typeAnnotation.typePath().orElse(""));
    annotation(canonical, typeAnnotation.annotation());
  }

  private static void annotationValue(
      StringBuilder canonical, String label, JavaAnnotationValue value) {
    switch (value) {
      case JavaAnnotationConstantValue constant ->
          append(canonical, label, "constant", constant(constant.value()));
      case JavaAnnotationClassValue classValue ->
          append(canonical, label, "class", classValue.descriptor());
      case JavaAnnotationEnumValue enumValue ->
          append(canonical, label, "enum", enumValue.type(), enumValue.constant());
      case JavaAnnotationNestedValue nested -> {
        append(canonical, label, "nested");
        annotation(canonical, nested.annotation());
      }
      case JavaAnnotationArrayValue array -> {
        append(canonical, label, "array", array.values().size());
        array.values().forEach(item -> annotationValue(canonical, "item", item));
      }
    }
  }

  private static void appendOptional(
      StringBuilder canonical, String label, java.util.Optional<String> value) {
    value.ifPresent(item -> append(canonical, label, item));
  }

  private static void append(StringBuilder canonical, Object... parts) {
    for (Object part : parts) {
      String value = Objects.toString(part);
      canonical.append(value.length()).append(':').append(value);
    }
    canonical.append('\n');
  }

  private static String constant(Object value) {
    return value.getClass().getName() + ":" + value;
  }
}
