package dev.w0fv1.norm.jvm;

import static dev.w0fv1.norm.jvm.BindingNames.bindingTypeSuffix;
import static dev.w0fv1.norm.jvm.BindingNames.simpleName;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BindingTypeNames(
    Map<String, String> references,
    Map<String, String> referencePaths,
    Map<JavaArrayType, String> arrays,
    Map<String, Map<String, String>> enumVariants,
    Map<String, Integer> typeParameterCounts,
    Map<String, JarBindingClassReference.Nominal> imports) {
  public BindingTypeNames {
    imports = Map.copyOf(imports);
    references = Map.copyOf(references);
    referencePaths = Map.copyOf(referencePaths);
    arrays = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(arrays));
    typeParameterCounts = Map.copyOf(typeParameterCounts);
    enumVariants =
        enumVariants.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
  }

  static void collectArrays(JavaBindingCallable callable, Set<JavaArrayType> arrays) {
    callable.parameters().forEach(type -> collectArrays(type, arrays));
    collectArrays(callable.returnType(), arrays);
  }

  static void collectArrays(JavaBindingType type, Set<JavaArrayType> arrays) {
    switch (type) {
      case JavaArrayType array -> {
        arrays.add(array);
        collectArrays(array.component(), arrays);
      }
      case JavaReferenceType reference ->
          reference
              .arguments()
              .forEach(
                  argument -> argument.type().ifPresent(value -> collectArrays(value, arrays)));
      case JavaBindingTypeVariable variable -> collectArrays(variable.erasure(), arrays);
      case JavaCallbackType callback -> {
        callback.parameters().forEach(parameter -> collectArrays(parameter, arrays));
        collectArrays(callback.returnType(), arrays);
      }
      case JavaBoxedType ignored -> {}
      case JavaPrimitiveType ignored -> {}
    }
  }

  static void collectReferences(JavaAnnotationValue value, Set<String> references) {
    if (value instanceof JavaAnnotationArrayValue array) {
      array.values().forEach(element -> collectReferences(element, references));
      return;
    }
    if (!(value instanceof JavaAnnotationClassValue classValue)) return;
    String descriptor = classValue.descriptor();
    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
      references.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
    }
  }

  static void collectReferences(JavaBindingCallable callable, Set<String> references) {
    callable
        .typeParameters()
        .forEach(
            parameter -> parameter.bound().ifPresent(type -> collectReferences(type, references)));
    callable.parameters().forEach(type -> collectReferences(type, references));
    collectReferences(callable.returnType(), references);
  }

  static void collectReferences(JavaBindingType type, Set<String> references) {
    switch (type) {
      case JavaArrayType array -> collectReferences(array.component(), references);
      case JavaBindingTypeVariable ignored -> {}
      case JavaCallbackType callback -> {
        callback.parameters().forEach(parameter -> collectReferences(parameter, references));
        collectReferences(callback.returnType(), references);
      }
      case JavaReferenceType reference -> {
        if (reference.kind() == JavaReferenceKind.OPAQUE
            || reference.kind() == JavaReferenceKind.ENUM
            || reference.kind() == JavaReferenceKind.RESOURCE) {
          references.add(reference.binaryName());
        }
        reference
            .arguments()
            .forEach(
                argument ->
                    argument.type().ifPresent(value -> collectReferences(value, references)));
      }
      case JavaBoxedType ignored -> {}
      case JavaPrimitiveType ignored -> {}
    }
  }

  static Map<JavaArrayType, String> allocateArrayNames(Set<JavaArrayType> arrays) {
    Map<String, List<JavaArrayType>> groups = new LinkedHashMap<>();
    arrays.forEach(
        array ->
            groups
                .computeIfAbsent(
                    array.component() instanceof JavaBindingTypeVariable
                        ? genericArrayName((JavaBindingTypeVariable) array.component())
                        : "Java" + bindingTypeSuffix(array.component()) + "Array",
                    ignored -> new ArrayList<>())
                .add(array));
    Map<JavaArrayType, String> names = new LinkedHashMap<>();
    groups.forEach(
        (base, values) ->
            values.forEach(
                array -> {
                  String name = base;
                  if (values.stream().map(JavaBindingType::descriptor).distinct().count() > 1) {
                    String digest =
                        Sha256Digest.compute(array.descriptor().getBytes(StandardCharsets.UTF_8))
                            .value();
                    name += "X" + digest.substring(0, 8);
                  }
                  names.put(array, name);
                }));
    return java.util.Collections.unmodifiableMap(names);
  }

  static String genericArrayName(JavaBindingTypeVariable component) {
    return "Java" + simpleName(component.erasure().displayName()) + "Array";
  }

  static String normRelationType(JavaReferenceType type, BindingTypeNames normTypes) {
    return normBoundType(type, normTypes);
  }

  static String normReturnType(JavaBindingCallable callable, BindingTypeNames normTypes) {
    return normType(
        callable.returnType(),
        normTypes,
        callable.kind() == JavaCallableKind.CONSTRUCTOR
            || callable.returnNullability() == JavaNullability.NON_NULL);
  }

  static String normType(
      JavaBindingType type, BindingTypeNames normTypes, boolean nonNullReference) {
    return normType(type, normTypes, nonNullReference, false);
  }

  private static String normType(
      JavaBindingType type,
      BindingTypeNames normTypes,
      boolean nonNullReference,
      boolean preserveTypeParameters) {
    return switch (type) {
      case JavaArrayType array ->
          normTypes.arrays().get(array)
              + (array.component() instanceof JavaBindingTypeVariable variable
                  ? "<" + variable.name() + ">"
                  : "")
              + (nonNullReference ? "" : "?");
      case JavaPrimitiveType primitive ->
          switch (primitive) {
            case BOOLEAN -> "Boolean";
            case BYTE, SHORT, INT -> "Integer";
            case LONG -> "Long";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case VOID -> "Void";
            case CHAR -> "CodePoint";
          };
      case JavaBoxedType boxed ->
          normType(boxed.primitive(), normTypes, false) + (nonNullReference ? "" : "?");
      case JavaBindingTypeVariable variable ->
          variable.name() + (nonNullReference || preserveTypeParameters ? "" : "?");
      case JavaCallbackType callback ->
          "Function<"
              + normType(callback.returnType(), normTypes, false, preserveTypeParameters)
              + callback.parameters().stream()
                  .map(parameter -> normType(parameter, normTypes, false, preserveTypeParameters))
                  .collect(java.util.stream.Collectors.joining(", ", "(", ")"))
              + ">"
              + (nonNullReference ? "" : "?");
      case JavaReferenceType reference ->
          switch (reference.kind()) {
            case OBJECT -> "Any" + (nonNullReference ? "" : "?");
            case CLASS -> {
              String arguments =
                  reference.arguments().isEmpty()
                      ? "<?>"
                      : reference.arguments().stream()
                          .map(argument -> normTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield "Class" + arguments + (nonNullReference ? "" : "?");
            }
            case STRING -> "String" + (nonNullReference ? "" : "?");
            case NUMBER -> "Number" + (nonNullReference ? "" : "?");
            case ENUM, OPAQUE, RESOURCE -> {
              String mapped = normTypes.references().get(reference.binaryName());
              if (mapped == null) {
                throw new IllegalArgumentException(
                    "Java type is not exported by this Module: " + reference.binaryName());
              }
              String arguments =
                  reference.arguments().isEmpty()
                      ? (normTypes.typeParameterCounts().getOrDefault(reference.binaryName(), 0)
                              == 0
                          ? ""
                          : java.util.stream.IntStream.range(
                                  0, normTypes.typeParameterCounts().get(reference.binaryName()))
                              .mapToObj(ignored -> "?")
                              .collect(java.util.stream.Collectors.joining(", ", "<", ">")))
                      : reference.arguments().stream()
                          .map(argument -> normTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield mapped + arguments + (nonNullReference ? "" : "?");
            }
          };
    };
  }

  static String normTypeArgument(JavaBindingTypeArgument argument, BindingTypeNames normTypes) {
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException("bounded Java wildcard cannot be represented in Norm");
    }
    return normType(argument.type().orElseThrow(), normTypes, true, true);
  }

  static String normBoundType(JavaBindingType type, BindingTypeNames normTypes) {
    if (type instanceof JavaBindingTypeVariable variable) return variable.name();
    if (!(type instanceof JavaReferenceType reference)) {
      throw new IllegalArgumentException(
          "Java generic bound cannot be represented in Norm: " + type.displayName());
    }
    return normType(reference, normTypes, true, true);
  }
}
