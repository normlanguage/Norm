package dev.w0fv1.norm.value;

import java.util.Map;
import java.util.Optional;

public final class AnnotationAbi {
  public static final String MODULE = "std";
  public static final String PACKAGE = "std.annotation";
  public static final String ANNOTATION_TARGET = "AnnotationTarget";
  public static final String ANNOTATION_RETENTION = "AnnotationRetention";
  public static final String FUNCTION_TARGET = "FunctionTarget";
  public static final String PARAMETER_TARGET = "ParameterTarget";
  public static final String FIELD_TARGET = "FieldTarget";
  public static final String BEFORE = "before";
  public static final String AROUND = "around";
  public static final String AFTER = "after";

  private static final Map<String, AnnotationTarget> TARGETS =
      Map.of(
          "PackageTarget",
          AnnotationTarget.PACKAGE,
          "TypeTarget",
          AnnotationTarget.TYPE,
          FIELD_TARGET,
          AnnotationTarget.FIELD,
          "ConstructorTarget",
          AnnotationTarget.CONSTRUCTOR,
          FUNCTION_TARGET,
          AnnotationTarget.FUNCTION,
          PARAMETER_TARGET,
          AnnotationTarget.PARAMETER,
          "LocalTarget",
          AnnotationTarget.LOCAL);
  private static final Map<String, AnnotationRetention> RETENTIONS =
      Map.of(
          "SourceRetention", AnnotationRetention.SOURCE,
          "BinaryRetention", AnnotationRetention.BINARY,
          "RuntimeRetention", AnnotationRetention.RUNTIME);

  private AnnotationAbi() {}

  public static Optional<AnnotationTarget> target(
      ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName)
        ? Optional.ofNullable(TARGETS.get(name))
        : Optional.empty();
  }

  public static Optional<AnnotationRetention> retention(
      ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName)
        ? Optional.ofNullable(RETENTIONS.get(name))
        : Optional.empty();
  }

  public static boolean isPolicyInterface(
      ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName)
        && (name.equals(ANNOTATION_TARGET)
            || name.equals(ANNOTATION_RETENTION)
            || TARGETS.containsKey(name)
            || RETENTIONS.containsKey(name));
  }

  public static boolean isFunctionTarget(ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName) && name.equals(FUNCTION_TARGET);
  }

  public static boolean isParameterTarget(
      ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName) && name.equals(PARAMETER_TARGET);
  }

  public static boolean isFieldTarget(ModuleCoordinate module, String packageName, String name) {
    return standard(module, packageName) && name.equals(FIELD_TARGET);
  }

  private static boolean standard(ModuleCoordinate module, String packageName) {
    return module.name().equals(MODULE) && packageName.equals(PACKAGE);
  }
}
