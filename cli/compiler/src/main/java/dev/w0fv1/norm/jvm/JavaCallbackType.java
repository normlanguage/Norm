package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaCallbackType(
    String binaryName,
    String methodName,
    List<JavaBindingType> parameters,
    JavaBindingType returnType)
    implements JavaBindingType {
  public JavaCallbackType {
    Objects.requireNonNull(binaryName, "binaryName");
    Objects.requireNonNull(methodName, "methodName");
    parameters = List.copyOf(parameters);
    Objects.requireNonNull(returnType, "returnType");
  }

  @Override
  public String descriptor() {
    return "L" + binaryName.replace('.', '/') + ";";
  }

  @Override
  public String displayName() {
    return binaryName;
  }
}
