package dev.w0fv1.norm.jvm;

final class JavaApiTypeNames {
  private JavaApiTypeNames() {}

  static boolean matches(String binaryName, String selectedName) {
    String canonicalName = binaryName.replace('$', '.');
    return canonicalName.equals(selectedName) || canonicalName.endsWith("." + selectedName);
  }
}
