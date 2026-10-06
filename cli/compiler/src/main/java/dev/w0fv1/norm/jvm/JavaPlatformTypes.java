package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import java.util.Map;
import java.util.Optional;

final class JavaPlatformTypes {
  private static final Map<JarBindingClassReference, String> CLASS_DESCRIPTORS =
      Map.ofEntries(
          Map.entry(new JarBindingClassReference.Builtin("std.core.Any"), "Ljava/lang/Object;"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.String"), "Ljava/lang/String;"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Number"), "Ljava/lang/Number;"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Integer"), "I"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Long"), "J"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Float"), "F"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Double"), "D"),
          Map.entry(new JarBindingClassReference.Builtin("std.core.Boolean"), "Z"));

  private JavaPlatformTypes() {}

  static Map<JarBindingClassReference, String> classDescriptors() {
    return CLASS_DESCRIPTORS;
  }

  static boolean isJavaBaseType(String binaryName) {
    int separator = binaryName.lastIndexOf('.');
    return separator > 0
        && Object.class.getModule().getPackages().contains(binaryName.substring(0, separator));
  }

  static Optional<JavaReferenceKind> referenceKind(String binaryName) {
    return switch (binaryName) {
      case "java.lang.Object" -> Optional.of(JavaReferenceKind.OBJECT);
      case "java.lang.Class" -> Optional.of(JavaReferenceKind.CLASS);
      case "java.lang.String" -> Optional.of(JavaReferenceKind.STRING);
      case "java.lang.Number" -> Optional.of(JavaReferenceKind.NUMBER);
      default -> Optional.empty();
    };
  }

  static boolean classTokenCompatible(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType ignored -> true;
      case JavaBindingTypeVariable ignored -> true;
      case JavaCallbackType ignored -> false;
      case JavaPrimitiveType ignored -> true;
      case JavaBoxedType ignored -> false;
      case JavaReferenceType reference -> reference.kind() != JavaReferenceKind.CLASS;
    };
  }
}
