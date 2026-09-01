package dev.w0fv1.norm.jvm;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record JavaBoxedType(String binaryName, JavaPrimitiveType primitive)
    implements JavaBindingType {
  private static final Map<String, JavaPrimitiveType> TYPES =
      Map.of(
          "java.lang.Boolean", JavaPrimitiveType.BOOLEAN,
          "java.lang.Byte", JavaPrimitiveType.BYTE,
          "java.lang.Short", JavaPrimitiveType.SHORT,
          "java.lang.Integer", JavaPrimitiveType.INT,
          "java.lang.Long", JavaPrimitiveType.LONG,
          "java.lang.Float", JavaPrimitiveType.FLOAT,
          "java.lang.Double", JavaPrimitiveType.DOUBLE,
          "java.lang.Character", JavaPrimitiveType.CHAR);

  public JavaBoxedType {
    Objects.requireNonNull(binaryName, "binaryName");
    Objects.requireNonNull(primitive, "primitive");
    if (primitive == JavaPrimitiveType.VOID || TYPES.get(binaryName) != primitive) {
      throw new IllegalArgumentException("invalid Java boxed primitive " + binaryName);
    }
  }

  public static Optional<JavaBoxedType> fromBinaryName(String binaryName) {
    return Optional.ofNullable(TYPES.get(binaryName))
        .map(primitive -> new JavaBoxedType(binaryName, primitive));
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
