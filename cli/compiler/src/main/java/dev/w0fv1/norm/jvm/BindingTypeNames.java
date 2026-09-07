package dev.w0fv1.norm.jvm;

import static dev.w0fv1.norm.jvm.BindingNames.bindingTypeSuffix;
import static dev.w0fv1.norm.jvm.BindingNames.simpleName;
import static dev.w0fv1.norm.jvm.JavaBindingMembers.requiredProtocolBinding;

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
    Map<String, Integer> typeParameterCounts) {
  public BindingTypeNames {
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
      case JavaBindingTypeVariable variable -> collectReferences(variable.erasure(), references);
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
    if (JavaGenericParameterProjector.isComparable(component.erasure())) {
      return "JavaComparableArray";
    }
    if (JavaGenericParameterProjector.isException(component.erasure())) {
      return "Java" + simpleName(component.erasure().displayName()) + "Array";
    }
    return "JavaObjectArray";
  }

  static String normRelationType(JavaReferenceType type, BindingTypeNames normTypes) {
    if (iterableRelation(type)) {
      return "Iterable<" + normReferenceArgument(type, 0, 1, normTypes) + ">";
    }
    return normBoundType(type, normTypes);
  }

  static boolean iterableRelation(JavaReferenceType type) {
    return switch (type.kind()) {
      case ITERABLE, COLLECTION, LIST, SET -> true;
      default -> false;
    };
  }

  static String normReturnType(JavaBindingCallable callable, BindingTypeNames normTypes) {
    if (requiredProtocolBinding(callable)) {
      return "Iterator<"
          + normReferenceArgument((JavaReferenceType) callable.returnType(), 0, 1, normTypes)
          + ">";
    }
    return normType(
        callable.returnType(),
        normTypes,
        callable.kind() == JavaCallableKind.CONSTRUCTOR
            || callable.returnNullability() == JavaNullability.NON_NULL);
  }

  static String normType(
      JavaBindingType type, BindingTypeNames normTypes, boolean nonNullReference) {
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
      case JavaBoxedType boxed -> normType(boxed.primitive(), normTypes, false) + "?";
      case JavaBindingTypeVariable variable -> variable.name() + (nonNullReference ? "" : "?");
      case JavaCallbackType callback ->
          "Function<"
              + normType(callback.returnType(), normTypes, false)
              + callback.parameters().stream()
                  .map(parameter -> normType(parameter, normTypes, false))
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
                          .map(argument -> normClassTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield "Class" + arguments + (nonNullReference ? "" : "?");
            }
            case OPTIONAL -> normType(referenceElement(reference), normTypes, false);
            case OPTIONAL_INT -> "Integer?";
            case OPTIONAL_LONG -> "Long?";
            case OPTIONAL_DOUBLE -> "Double?";
            case ITERABLE ->
                "IterableView<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case ITERATOR ->
                "IteratorView<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case COLLECTION ->
                "MutableCollection<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case LIST ->
                "MutableList<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case SET ->
                "MutableSet<"
                    + normReferenceArgument(reference, 0, 1, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case MAP ->
                "MutableMap<"
                    + normReferenceArgument(reference, 0, 2, normTypes)
                    + ", "
                    + normReferenceArgument(reference, 1, 2, normTypes)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case STRING -> "String" + (nonNullReference ? "" : "?");
            case UNIT -> "Unit" + (nonNullReference ? "" : "?");
            case CHAR_SEQUENCE -> "String" + (nonNullReference ? "" : "?");
            case CHARSET -> "String" + (nonNullReference ? "" : "?");
            case NUMBER -> "Number" + (nonNullReference ? "" : "?");
            case EXCEPTION -> "Exception" + (nonNullReference ? "" : "?");
            case INPUT_STREAM -> "InputStream" + (nonNullReference ? "" : "?");
            case OUTPUT_STREAM -> "OutputStream" + (nonNullReference ? "" : "?");
            case TASK ->
                "Task<"
                    + normType(referenceElement(reference), normTypes, false)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case PUBLISHER ->
                "Publisher<"
                    + normType(referenceElement(reference), normTypes, false)
                    + ">"
                    + (nonNullReference ? "" : "?");
            case DURATION -> "Duration" + (nonNullReference ? "" : "?");
            case URI -> "Uri" + (nonNullReference ? "" : "?");
            case PATH, FILE -> "Path" + (nonNullReference ? "" : "?");
            case ENUM, OPAQUE, RESOURCE -> {
              String mapped = normTypes.references().get(reference.binaryName());
              if (mapped == null) {
                throw new IllegalArgumentException(
                    "Java type is not exported by this Module: " + reference.binaryName());
              }
              String arguments =
                  reference.arguments().isEmpty()
                      ? ""
                      : reference.arguments().stream()
                          .map(argument -> normTypeArgument(argument, normTypes))
                          .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
              yield mapped + arguments + (nonNullReference ? "" : "?");
            }
          };
    };
  }

  static JavaBindingType referenceElement(JavaReferenceType reference) {
    return referenceArgument(reference, 0, 1);
  }

  static JavaBindingType referenceArgument(JavaReferenceType reference, int index, int arity) {
    if (reference.arguments().isEmpty()) {
      return new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    }
    if (reference.arguments().size() != arity) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    JavaBindingTypeArgument argument = reference.arguments().get(index);
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) {
      return new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    }
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    return argument.type().orElseThrow();
  }

  static String normReferenceArgument(
      JavaReferenceType reference, int index, int arity, BindingTypeNames normTypes) {
    if (reference.arguments().isEmpty()) {
      return normType(
          new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT), normTypes, false);
    }
    if (reference.arguments().size() != arity) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    JavaBindingTypeArgument argument = reference.arguments().get(index);
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException(
          "Java reference type argument cannot be represented in Norm: " + reference.binaryName());
    }
    return normType(argument.type().orElseThrow(), normTypes, false);
  }

  static String normTypeArgument(JavaBindingTypeArgument argument, BindingTypeNames normTypes) {
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException("bounded Java wildcard cannot be represented in Norm");
    }
    return normType(argument.type().orElseThrow(), normTypes, false);
  }

  static String normClassTypeArgument(
      JavaBindingTypeArgument argument, BindingTypeNames normTypes) {
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return "?";
    if (argument.variance() != JavaTypeVariance.EXACT) {
      throw new IllegalArgumentException("bounded Java wildcard cannot be represented in Norm");
    }
    return normType(argument.type().orElseThrow(), normTypes, true);
  }

  static String normBoundType(JavaBindingType type, BindingTypeNames normTypes) {
    if (type instanceof JavaBindingTypeVariable variable) return variable.name();
    if (JavaGenericParameterProjector.isException(type)) return "Exception";
    if (!(type instanceof JavaReferenceType reference)) {
      throw new IllegalArgumentException(
          "Java generic bound cannot be represented in Norm: " + type.displayName());
    }
    if (reference.kind() == JavaReferenceKind.RESOURCE
        && (reference.binaryName().equals("java.lang.AutoCloseable")
            || reference.binaryName().equals("java.io.Closeable"))) {
      return "Resource";
    }
    if (!JavaGenericParameterProjector.isComparable(reference)) {
      if (!reference.arguments().isEmpty()
          && (reference.kind() == JavaReferenceKind.OPAQUE
              || reference.kind() == JavaReferenceKind.RESOURCE)) {
        String mapped = normTypes.references().get(reference.binaryName());
        if (mapped == null) {
          throw new IllegalArgumentException(
              "Java generic bound is not exported by this Module: " + type.displayName());
        }
        return mapped
            + reference.arguments().stream()
                .map(argument -> normClassTypeArgument(argument, normTypes))
                .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
      }
      return normType(reference, normTypes, true);
    }
    if (reference.arguments().size() != 1) {
      throw new IllegalArgumentException(
          "Java generic bound cannot be represented in Norm: " + type.displayName());
    }
    JavaBindingType argument = reference.arguments().getFirst().type().orElseThrow();
    return "Comparable<" + normType(argument, normTypes, true) + ">";
  }

  static boolean containsException(JavaBindingCallable callable) {
    return callable.parameters().stream().anyMatch(BindingTypeNames::containsException)
        || containsException(callable.returnType());
  }

  static boolean containsException(JavaBindingType type) {
    return containsReferenceKind(type, JavaReferenceKind.EXCEPTION);
  }

  static boolean containsPath(JavaBindingCallable callable) {
    return callable.parameters().stream().anyMatch(BindingTypeNames::containsPath)
        || containsPath(callable.returnType());
  }

  static boolean containsPath(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> containsPath(array.component());
      case JavaBindingTypeVariable variable -> containsPath(variable.erasure());
      case JavaCallbackType callback ->
          callback.parameters().stream().anyMatch(BindingTypeNames::containsPath)
              || containsPath(callback.returnType());
      case JavaReferenceType reference ->
          reference.kind() == JavaReferenceKind.PATH || reference.kind() == JavaReferenceKind.FILE;
      case JavaBoxedType ignored -> false;
      case JavaPrimitiveType ignored -> false;
    };
  }

  static boolean containsReferenceKind(JavaBindingCallable callable, JavaReferenceKind kind) {
    return callable.parameters().stream().anyMatch(type -> containsReferenceKind(type, kind))
        || containsReferenceKind(callable.returnType(), kind);
  }

  static boolean containsReferenceKind(JavaBindingType type, JavaReferenceKind kind) {
    return switch (type) {
      case JavaArrayType array -> containsReferenceKind(array.component(), kind);
      case JavaBindingTypeVariable variable -> containsReferenceKind(variable.erasure(), kind);
      case JavaCallbackType callback ->
          callback.parameters().stream()
                  .anyMatch(parameter -> containsReferenceKind(parameter, kind))
              || containsReferenceKind(callback.returnType(), kind);
      case JavaReferenceType reference ->
          reference.kind() == kind
              || reference.arguments().stream()
                  .flatMap(argument -> argument.type().stream())
                  .anyMatch(argument -> containsReferenceKind(argument, kind));
      case JavaBoxedType ignored -> false;
      case JavaPrimitiveType ignored -> false;
    };
  }

  static String genericTypeDeclaration(JavaBindingTypeVariable variable) {
    if (JavaGenericParameterProjector.isComparable(variable.erasure())) {
      return "<T extends Comparable<T>>";
    }
    if (JavaGenericParameterProjector.isException(variable.erasure())) {
      return "<T extends Exception>";
    }
    return "<T>";
  }
}
