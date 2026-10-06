package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class JavaBindingMembers {
  private final Map<String, JavaApiType> apiTypes;
  private final JavaTypeProjector projector;

  JavaBindingMembers(Map<String, JavaApiType> apiTypes) {
    this.apiTypes = Map.copyOf(apiTypes);
    this.projector = JavaTypeProjector.forSchema(apiTypes);
  }

  static JavaArrayType arraySupportType(JavaArrayType array) {
    if (!(array.component() instanceof JavaBindingTypeVariable variable)) return array;
    return new JavaArrayType(new JavaBindingTypeVariable("T", variable.erasure()));
  }

  static JavaBindingCallable arrayConstructor(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "<array>",
        "(I)" + array.descriptor(),
        JavaCallableKind.ARRAY_CONSTRUCTOR,
        List.of(JavaPrimitiveType.INT),
        array);
  }

  static JavaBindingCallable arrayLength(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "length",
        "()I",
        JavaCallableKind.ARRAY_LENGTH,
        List.of(),
        JavaPrimitiveType.INT);
  }

  static JavaBindingCallable arrayGet(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "get",
        "(I)" + array.component().descriptor(),
        JavaCallableKind.ARRAY_GET,
        List.of(JavaPrimitiveType.INT),
        array.component());
  }

  static JavaBindingCallable arraySet(JavaArrayType array) {
    return new JavaBindingCallable(
        array.descriptor(),
        "set",
        "(I" + array.component().descriptor() + ")V",
        JavaCallableKind.ARRAY_SET,
        List.of(JavaPrimitiveType.INT, array.component()),
        JavaPrimitiveType.VOID);
  }

  List<JavaBindingCallable> bindings(JavaApiType owner) {
    List<JavaBindingCallable> bindings = new ArrayList<>();
    owner.fields().stream().flatMap(field -> field.bindings().stream()).forEach(bindings::add);
    owner.effectiveMethods().stream()
        .flatMap(method -> method.binding().stream())
        .forEach(bindings::add);
    return List.copyOf(bindings);
  }

  Optional<JavaReferenceType> projectedSuperclass(JavaApiType owner) {
    if (owner.kind() == JavaApiTypeKind.INTERFACE
        || owner.kind() == JavaApiTypeKind.ENUM
        || owner.kind() == JavaApiTypeKind.ANNOTATION) return Optional.empty();
    return owner
        .signature()
        .superclass()
        .flatMap(
            relation ->
                projector.project(
                    relation, classVariables(owner), JavaTypeProjector.Position.VALUE))
        .filter(JavaReferenceType.class::isInstance)
        .map(JavaReferenceType.class::cast)
        .filter(type -> type.kind() == JavaReferenceKind.OPAQUE);
  }

  List<JavaReferenceType> projectedInterfaces(JavaApiType owner) {
    Map<String, JavaReferenceType> projected = new LinkedHashMap<>();
    Map<String, JavaBindingType> variables = classVariables(owner);
    projectedInterfaces(owner, variables, new java.util.HashSet<>(), projected);
    return List.copyOf(projected.values());
  }

  void projectedInterfaces(
      JavaApiType owner,
      Map<String, JavaBindingType> variables,
      Set<String> visited,
      Map<String, JavaReferenceType> projected) {
    if (!visited.add(owner.binaryName())) return;
    for (JavaClassTypeSignature relation : owner.signature().interfaces()) {
      JavaApiType interfaceType = apiTypes.get(relation.binaryName());
      if (interfaceType == null || interfaceType.kind() != JavaApiTypeKind.INTERFACE) continue;
      JavaBindingType binding =
          projector.project(relation, variables, JavaTypeProjector.Position.VALUE).orElse(null);
      if (!(binding instanceof JavaReferenceType reference)) {
        throw new IllegalArgumentException(
            "Java interface relation cannot be represented in Norm: " + relation.binaryName());
      }
      projected.putIfAbsent(reference.displayName(), reference);
    }
    if (owner.kind() == JavaApiTypeKind.INTERFACE || owner.signature().superclass().isEmpty()) {
      return;
    }
    JavaClassTypeSignature relation = owner.signature().superclass().orElseThrow();
    JavaApiType parent = apiTypes.get(relation.binaryName());
    if (parent == null) return;
    parentVariables(parent, relation, variables)
        .ifPresent(
            parentVariables -> projectedInterfaces(parent, parentVariables, visited, projected));
  }

  Map<String, JavaBindingType> classVariables(JavaApiType owner) {
    return Map.copyOf(
        JavaGenericParameterProjector.project(
                owner.signature().typeParameters(),
                Map.of(),
                (signature, variables) ->
                    projector
                        .project(
                            signature,
                            new LinkedHashMap<>(variables),
                            JavaTypeProjector.Position.VALUE)
                        .orElse(null))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Java generic bound cannot be represented in Norm: " + owner.binaryName()))
            .variables());
  }

  java.util.Optional<Map<String, JavaBindingType>> parentVariables(
      JavaApiType parent, JavaClassTypeSignature relation, Map<String, JavaBindingType> variables) {
    List<JavaTypeArgument> arguments =
        relation.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    if (arguments.size() != parent.signature().typeParameters().size()) {
      return java.util.Optional.empty();
    }
    Map<String, JavaBindingType> result = new LinkedHashMap<>();
    for (int index = 0; index < arguments.size(); index++) {
      JavaTypeArgument argument = arguments.get(index);
      if (argument.variance() != JavaTypeVariance.EXACT) {
        return java.util.Optional.empty();
      }
      JavaBindingType type =
          projector
              .project(argument.type().orElseThrow(), variables, JavaTypeProjector.Position.VALUE)
              .orElse(null);
      if (type == null) {
        return java.util.Optional.empty();
      }
      result.put(parent.signature().typeParameters().get(index).name(), type);
    }
    return java.util.Optional.of(Map.copyOf(result));
  }

  static String methodKey(String name, String descriptor) {
    return name + descriptor;
  }

  static String methodShape(JavaBindingCallable callable) {
    return callable.name()
        + callable.parameters().stream()
            .map(JavaBindingType::descriptor)
            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  static boolean enumConstant(JavaApiType owner, JavaBindingCallable callable) {
    return callable.kind() == JavaCallableKind.STATIC_FIELD_GET
        && owner.fields().stream()
            .anyMatch(
                field ->
                    field.name().equals(callable.name())
                        && (field.modifiers() & org.objectweb.asm.Opcodes.ACC_ENUM) != 0);
  }

  List<JavaBindingTypeParameter> classTypeParameters(JavaApiType owner) {
    return JavaGenericParameterProjector.project(
            owner.signature().typeParameters(),
            Map.of(),
            (signature, variables) ->
                projector
                    .project(
                        signature, new LinkedHashMap<>(variables), JavaTypeProjector.Position.VALUE)
                    .orElse(null))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Java generic bound cannot be represented in Norm: " + owner.binaryName()))
        .parameters();
  }

  static List<JavaBindingTypeParameter> constructorTypeParameters(
      List<JavaBindingTypeParameter> ownerTypeParameters, JavaBindingCallable callable) {
    return java.util.stream.Stream.concat(
            ownerTypeParameters.stream(), callable.typeParameters().stream())
        .toList();
  }

  static List<JavaBindingType> bounds(
      List<JavaBindingTypeParameter> ownerParameters, List<JavaBindingCallable> bindings) {
    return java.util.stream.Stream.concat(
            ownerParameters.stream(),
            bindings.stream().flatMap(callable -> callable.typeParameters().stream()))
        .map(JavaBindingTypeParameter::bound)
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  Set<String> resourceTypes() {
    return apiTypes.keySet().stream()
        .filter(name -> isResourceType(name, new java.util.HashSet<>()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private boolean isResourceType(String name, Set<String> visited) {
    if (name.equals("java.lang.AutoCloseable") || name.equals("java.io.Closeable")) return true;
    if (!visited.add(name)) return false;
    JavaApiType type = apiTypes.get(name);
    if (type == null) return false;
    return java.util.stream.Stream.concat(
            type.signature().superclass().stream(), type.signature().interfaces().stream())
        .map(JavaClassTypeSignature::binaryName)
        .anyMatch(parent -> isResourceType(parent, visited));
  }

  static JavaBindingCallable markResources(JavaBindingCallable callable, Set<String> resources) {
    return new JavaBindingCallable(
        callable.owner(),
        callable.name(),
        callable.descriptor(),
        callable.kind(),
        callable.typeParameters().stream()
            .map(
                parameter ->
                    new JavaBindingTypeParameter(
                        parameter.name(),
                        parameter.bound().map(type -> markResources(type, resources))))
            .toList(),
        callable.parameters().stream().map(type -> markResources(type, resources)).toList(),
        markResources(callable.returnType(), resources),
        callable.returnNullability(),
        callable.ownership());
  }

  static JavaBindingType markResources(JavaBindingType type, Set<String> resources) {
    return switch (type) {
      case JavaArrayType array -> new JavaArrayType(markResources(array.component(), resources));
      case JavaBindingTypeVariable variable ->
          new JavaBindingTypeVariable(
              variable.name(), markResources(variable.erasure(), resources));
      case JavaCallbackType callback ->
          new JavaCallbackType(
              callback.binaryName(),
              callback.methodName(),
              callback.parameters().stream()
                  .map(parameter -> markResources(parameter, resources))
                  .toList(),
              markResources(callback.returnType(), resources));
      case JavaReferenceType reference ->
          new JavaReferenceType(
              reference.binaryName(),
              resources.contains(reference.binaryName())
                  ? JavaReferenceKind.RESOURCE
                  : reference.kind(),
              reference.arguments().stream()
                  .map(
                      argument ->
                          argument.type().isEmpty()
                              ? argument
                              : new JavaBindingTypeArgument(
                                  argument.variance(),
                                  Optional.of(
                                      markResources(argument.type().orElseThrow(), resources))))
                  .toList());
      case JavaBoxedType boxed -> boxed;
      case JavaPrimitiveType primitive -> primitive;
    };
  }
}
