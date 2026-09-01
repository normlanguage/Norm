package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaBindingCallable(
    String owner,
    String name,
    String descriptor,
    JavaCallableKind kind,
    List<JavaBindingTypeParameter> typeParameters,
    List<JavaBindingType> parameters,
    JavaBindingType returnType,
    JavaNullability returnNullability) {
  public JavaBindingCallable(
      String owner,
      String name,
      String descriptor,
      JavaCallableKind kind,
      List<JavaBindingTypeParameter> typeParameters,
      List<JavaBindingType> parameters,
      JavaBindingType returnType) {
    this(
        owner,
        name,
        descriptor,
        kind,
        typeParameters,
        parameters,
        returnType,
        JavaNullability.UNKNOWN);
  }

  public JavaBindingCallable(
      String owner,
      String name,
      String descriptor,
      JavaCallableKind kind,
      List<JavaBindingType> parameters,
      JavaBindingType returnType) {
    this(owner, name, descriptor, kind, List.of(), parameters, returnType, JavaNullability.UNKNOWN);
  }

  public JavaBindingCallable {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(kind, "kind");
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    Objects.requireNonNull(returnType, "returnType");
    Objects.requireNonNull(returnNullability, "returnNullability");
  }
}
