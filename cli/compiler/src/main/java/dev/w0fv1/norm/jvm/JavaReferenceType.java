package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record JavaReferenceType(
    String binaryName, JavaReferenceKind kind, List<JavaBindingTypeArgument> arguments)
    implements JavaBindingType {
  public JavaReferenceType(String binaryName, JavaReferenceKind kind) {
    this(binaryName, kind, List.of());
  }

  public JavaReferenceType {
    Objects.requireNonNull(binaryName, "binaryName");
    Objects.requireNonNull(kind, "kind");
    arguments = List.copyOf(arguments);
    if (binaryName.isBlank())
      throw new IllegalArgumentException("Java type name must not be blank");
  }

  @Override
  public String descriptor() {
    return "L" + binaryName.replace('.', '/') + ";";
  }

  @Override
  public String displayName() {
    if (arguments.isEmpty()) return binaryName;
    return binaryName
        + arguments.stream()
            .map(argument -> argument.type().map(JavaBindingType::displayName).orElse("?"))
            .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
  }
}
