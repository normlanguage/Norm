package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaMethodSignature(
    List<JavaTypeParameter> typeParameters,
    List<JavaTypeSignature> parameters,
    JavaTypeSignature returnType,
    List<JavaTypeSignature> exceptions) {
  public JavaMethodSignature {
    typeParameters = List.copyOf(typeParameters);
    parameters = List.copyOf(parameters);
    Objects.requireNonNull(returnType, "returnType");
    exceptions = List.copyOf(exceptions);
  }
}
