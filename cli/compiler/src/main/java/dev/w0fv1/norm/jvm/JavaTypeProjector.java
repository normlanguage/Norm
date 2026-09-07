package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JavaTypeProjector {
  public enum Position {
    VALUE,
    PARAMETER
  }

  public record FunctionalInterface(
      List<JavaTypeParameter> typeParameters, String methodName, JavaMethodSignature signature) {
    public FunctionalInterface {
      typeParameters = List.copyOf(typeParameters);
      Objects.requireNonNull(methodName);
      Objects.requireNonNull(signature);
    }
  }

  private final Map<String, JavaReferenceKind> rootTypes;
  private final Map<String, FunctionalInterface> samTypes;

  public JavaTypeProjector(
      Map<String, JavaReferenceKind> types, Map<String, FunctionalInterface> callbacks) {
    rootTypes = Map.copyOf(types);
    samTypes = Map.copyOf(callbacks);
  }

  public Optional<JavaBindingType> project(
      JavaTypeSignature signature,
      Map<String, ? extends JavaBindingType> variables,
      Position position) {
    Objects.requireNonNull(position);
    return Optional.ofNullable(resolve(signature, variables, position == Position.PARAMETER));
  }

  public static JavaTypeProjector forSchema(Map<String, JavaApiType> types) {
    Map<String, JavaReferenceKind> kinds = new LinkedHashMap<>();
    types.forEach(
        (name, type) ->
            kinds.put(
                name,
                type.kind() == JavaApiTypeKind.ENUM
                    ? JavaReferenceKind.ENUM
                    : JavaReferenceKind.OPAQUE));
    return new JavaTypeProjector(kinds, Map.of());
  }

  private JavaBindingType resolve(
      JavaTypeSignature signature,
      Map<String, ? extends JavaBindingType> variables,
      boolean allowCallback) {
    return switch (signature) {
      case JavaPrimitiveTypeSignature primitive -> primitive.type();
      case JavaTypeVariableSignature variable -> variables.get(variable.name());
      case JavaArrayTypeSignature array -> {
        JavaBindingType component = resolve(array.component(), variables, false);
        yield component == null ? null : new JavaArrayType(component);
      }
      case JavaClassTypeSignature classType ->
          bindingClassType(classType, variables, allowCallback);
    };
  }

  private JavaBindingType bindingClassType(
      JavaClassTypeSignature classType,
      Map<String, ? extends JavaBindingType> variables,
      boolean allowCallback) {
    String name = classType.binaryName();
    Optional<JavaBoxedType> boxed = JavaBoxedType.fromBinaryName(name);
    if (boxed.isPresent()
        && classType.segments().stream().allMatch(segment -> segment.arguments().isEmpty())) {
      return boxed.orElseThrow();
    }
    if (allowCallback) {
      Optional<JavaCallbackType> callback =
          JavaPlatformCallbacks.project(
              classType, signature -> resolve(signature, variables, false));
      if (callback.isPresent()) return callback.orElseThrow();
      callback = projectSam(classType, variables);
      if (callback.isPresent()) return callback.orElseThrow();
    }
    JavaReferenceKind kind = JavaPlatformTypes.referenceKind(name).orElse(rootTypes.get(name));
    if (kind == null) return null;
    List<JavaBindingTypeArgument> arguments = new ArrayList<>();
    for (JavaClassTypeSegment segment : classType.segments()) {
      for (JavaTypeArgument argument : segment.arguments()) {
        if (argument.variance() == JavaTypeVariance.UNBOUNDED) {
          arguments.add(JavaBindingTypeArgument.unbounded());
          continue;
        }
        if (kind == JavaReferenceKind.CLASS && argument.variance() == JavaTypeVariance.EXTENDS) {
          arguments.add(JavaBindingTypeArgument.unbounded());
          continue;
        }
        if (argument.variance() != JavaTypeVariance.EXACT) return null;
        JavaBindingType type = resolve(argument.type().orElseThrow(), variables, false);
        if (type == null) return null;
        arguments.add(JavaBindingTypeArgument.exact(type));
      }
    }
    if (kind == JavaReferenceKind.CLASS
        && arguments.stream()
            .filter(argument -> argument.variance() == JavaTypeVariance.EXACT)
            .map(argument -> argument.type().orElseThrow())
            .anyMatch(type -> !JavaPlatformTypes.classTokenCompatible(type))) {
      return null;
    }
    return new JavaReferenceType(name, kind, arguments);
  }

  private Optional<JavaCallbackType> projectSam(
      JavaClassTypeSignature type, Map<String, ? extends JavaBindingType> outerVariables) {
    FunctionalInterface sam = samTypes.get(type.binaryName());
    if (sam == null) return Optional.empty();
    List<JavaTypeArgument> arguments =
        type.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    List<JavaTypeParameter> parameters = sam.typeParameters();
    if (!arguments.isEmpty() && arguments.size() != parameters.size()) return Optional.empty();
    Map<String, JavaBindingType> variables = new LinkedHashMap<>(outerVariables);
    JavaBindingType object = new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);

    for (int index = 0; index < parameters.size(); index++) {
      JavaBindingType argument = object;
      if (!arguments.isEmpty() && arguments.get(index).variance() != JavaTypeVariance.UNBOUNDED) {
        argument = resolve(arguments.get(index).type().orElseThrow(), outerVariables, false);
        if (argument == null) return Optional.empty();
      }
      variables.put(parameters.get(index).name(), argument);
    }
    JavaMethodSignature signature = sam.signature();
    if (!signature.typeParameters().isEmpty()) return Optional.empty();
    List<JavaBindingType> callbackParameters = new ArrayList<>();
    for (JavaTypeSignature parameter : signature.parameters()) {
      JavaBindingType projected = resolve(parameter, variables, false);
      if (projected == null || !exposableValue(projected)) return Optional.empty();
      callbackParameters.add(projected);
    }
    JavaBindingType returnType = resolve(signature.returnType(), variables, false);
    if (returnType == null || !exposableValue(returnType)) return Optional.empty();
    return Optional.of(
        new JavaCallbackType(type.binaryName(), sam.methodName(), callbackParameters, returnType));
  }

  static boolean exposableParameter(JavaBindingType type) {
    if (type instanceof JavaCallbackType callback) {
      return callback.parameters().stream().allMatch(JavaTypeProjector::exposableValue)
          && exposableValue(callback.returnType());
    }
    return exposableValue(type);
  }

  static boolean exposableValue(JavaBindingType type) {
    return switch (type) {
      case JavaPrimitiveType primitive -> true;
      case JavaBoxedType ignored -> true;
      case JavaBindingTypeVariable ignored -> true;
      case JavaCallbackType ignored -> false;
      case JavaArrayType array ->
          (array.component() instanceof JavaBindingTypeVariable || concrete(array.component()))
              && exposableValue(array.component());
      case JavaReferenceType ignored -> true;
    };
  }

  private static boolean concrete(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> concrete(array.component());
      case JavaBindingTypeVariable ignored -> false;
      case JavaCallbackType ignored -> false;
      case JavaReferenceType reference ->
          reference.arguments().stream()
              .allMatch(
                  argument ->
                      (reference.kind() == JavaReferenceKind.CLASS
                              && argument.variance() == JavaTypeVariance.UNBOUNDED)
                          || (argument.variance() == JavaTypeVariance.EXACT
                              && concrete(argument.type().orElseThrow())));
      case JavaBoxedType ignored -> true;
      case JavaPrimitiveType ignored -> true;
    };
  }
}
