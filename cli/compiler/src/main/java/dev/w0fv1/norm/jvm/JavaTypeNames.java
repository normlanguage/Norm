package dev.w0fv1.norm.jvm;

final class JavaTypeNames {
  private JavaTypeNames() {}

  static String sourceName(String binaryName) {
    return binaryName.replace('$', '.');
  }

  static boolean matches(String binaryName, String selectedName) {
    String sourceName = sourceName(binaryName);
    return sourceName.equals(selectedName) || sourceName.endsWith("." + selectedName);
  }
}
