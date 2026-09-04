package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CoreNominalTypeKey;
import java.util.Objects;

public final class JavaApplicationTypeName {
  public static final String LOCAL_PACKAGE = "norm.generated.application";

  private JavaApplicationTypeName() {}

  public static String packageName(String normPackageName) {
    String value = Objects.requireNonNull(normPackageName, "normPackageName");
    return value.isEmpty() ? LOCAL_PACKAGE : value;
  }

  public static String binaryName(CoreNominalTypeKey nominal) {
    Objects.requireNonNull(nominal, "nominal");
    return packageName(nominal.packageName()) + "." + nominal.name();
  }
}
