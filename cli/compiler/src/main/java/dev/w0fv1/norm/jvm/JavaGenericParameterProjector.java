package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class JavaGenericParameterProjector {
  private static final String OBJECT = "java.lang.Object";
  private static final String COMPARABLE = "java.lang.Comparable";

  private JavaGenericParameterProjector() {}

  static Optional<Projection> project(
      List<JavaTypeParameter> parameters,
      Map<String, JavaBindingTypeVariable> inherited,
      BindingTypeResolver resolver) {
    Map<String, JavaBindingTypeVariable> variables = new LinkedHashMap<>(inherited);
    List<JavaBindingTypeParameter> projected = new ArrayList<>();
    for (JavaTypeParameter parameter : parameters) {
      JavaBindingType erasure = erasure(parameter, variables);
      if (erasure == null) return Optional.empty();
      JavaBindingTypeVariable variable =
          new JavaBindingTypeVariable(parameter.name(), concreteErasure(erasure));
      variables.put(parameter.name(), variable);
      Optional<Optional<JavaBindingType>> bound = bound(parameter, variables, resolver);
      if (bound.isEmpty()) return Optional.empty();
      projected.add(new JavaBindingTypeParameter(parameter.name(), bound.orElseThrow()));
    }
    return Optional.of(new Projection(Map.copyOf(variables), List.copyOf(projected)));
  }

  static boolean isComparable(JavaBindingType type) {
    return type instanceof JavaReferenceType reference && reference.binaryName().equals(COMPARABLE);
  }

  static boolean isException(JavaBindingType type) {
    return type instanceof JavaReferenceType reference
        && JavaPlatformTypes.isException(reference.binaryName());
  }

  static boolean isException(String binaryName) {
    return JavaPlatformTypes.isException(binaryName);
  }

  private static Optional<Optional<JavaBindingType>> bound(
      JavaTypeParameter parameter,
      Map<String, JavaBindingTypeVariable> variables,
      BindingTypeResolver resolver) {
    List<JavaTypeSignature> bounds = new ArrayList<>();
    parameter.classBound().filter(value -> !isObject(value)).ifPresent(bounds::add);
    bounds.addAll(parameter.interfaceBounds());
    if (bounds.isEmpty()) return Optional.of(Optional.empty());
    if (bounds.size() != 1) return Optional.empty();
    JavaTypeSignature bound = bounds.getFirst();
    if (bound instanceof JavaTypeVariableSignature variable) {
      return Optional.of(Optional.ofNullable(variables.get(variable.name())));
    }
    if (!(bound instanceof JavaClassTypeSignature classType)) return Optional.empty();
    List<JavaTypeArgument> arguments =
        classType.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    if (JavaPlatformTypes.isException(classType.binaryName())) {
      return arguments.isEmpty()
          ? Optional.of(
              Optional.of(new JavaReferenceType(classType.binaryName(), JavaReferenceKind.OPAQUE)))
          : Optional.empty();
    }
    if (classType.binaryName().equals(COMPARABLE)) {
      if (arguments.size() != 1) return Optional.empty();
      JavaTypeArgument argument = arguments.getFirst();
      if (argument.variance() != JavaTypeVariance.EXACT
          && argument.variance() != JavaTypeVariance.SUPER) {
        return Optional.empty();
      }
      JavaBindingType projectedArgument =
          resolver.resolve(argument.type().orElseThrow(), variables);
      if (projectedArgument == null) return Optional.empty();
      return Optional.of(
          Optional.of(
              new JavaReferenceType(
                  COMPARABLE,
                  JavaReferenceKind.OPAQUE,
                  List.of(JavaBindingTypeArgument.exact(projectedArgument)))));
    }
    JavaBindingType resolved = resolver.resolve(bound, variables);
    if (resolved == null) return Optional.empty();
    return Optional.of(representableNormBound(resolved) ? Optional.of(resolved) : Optional.empty());
  }

  private static boolean representableNormBound(JavaBindingType type) {
    if (type instanceof JavaBindingTypeVariable) return true;
    if (!(type instanceof JavaReferenceType reference)) return false;
    return switch (reference.kind()) {
      case OBJECT,
          ENUM,
          OPTIONAL,
          OPTIONAL_INT,
          OPTIONAL_LONG,
          OPTIONAL_DOUBLE,
          STRING,
          UNIT,
          CHAR_SEQUENCE,
          CHARSET,
          NUMBER ->
          false;
      default -> true;
    };
  }

  private static JavaBindingType erasure(
      JavaTypeParameter parameter, Map<String, JavaBindingTypeVariable> variables) {
    Optional<JavaTypeSignature> bound =
        parameter.classBound().isPresent()
            ? parameter.classBound()
            : parameter.interfaceBounds().stream().findFirst();
    if (bound.isEmpty()) return new JavaReferenceType(OBJECT, JavaReferenceKind.OPAQUE);
    return erasedType(bound.orElseThrow(), variables);
  }

  private static JavaBindingType erasedType(
      JavaTypeSignature signature, Map<String, JavaBindingTypeVariable> variables) {
    return switch (signature) {
      case JavaPrimitiveTypeSignature primitive -> primitive.type();
      case JavaTypeVariableSignature variable -> variables.get(variable.name());
      case JavaClassTypeSignature classType ->
          new JavaReferenceType(classType.binaryName(), JavaReferenceKind.OPAQUE);
      case JavaArrayTypeSignature array -> {
        JavaBindingType component = erasedType(array.component(), variables);
        yield component == null ? null : new JavaArrayType(component);
      }
    };
  }

  private static JavaBindingType concreteErasure(JavaBindingType type) {
    JavaBindingType result = type;
    while (result instanceof JavaBindingTypeVariable variable) result = variable.erasure();
    return result;
  }

  private static boolean isObject(JavaTypeSignature signature) {
    return signature instanceof JavaClassTypeSignature classType
        && classType.binaryName().equals(OBJECT);
  }

  @FunctionalInterface
  interface BindingTypeResolver {
    JavaBindingType resolve(
        JavaTypeSignature signature, Map<String, JavaBindingTypeVariable> variables);
  }

  record Projection(
      Map<String, JavaBindingTypeVariable> variables, List<JavaBindingTypeParameter> parameters) {}
}
