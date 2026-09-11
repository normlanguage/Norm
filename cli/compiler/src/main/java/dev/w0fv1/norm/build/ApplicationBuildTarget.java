package dev.w0fv1.norm.build;

public enum ApplicationBuildTarget {
  NATIVE,
  JVM;

  public void validateDiagnostics(boolean diagnostics) {
    if (diagnostics && this != NATIVE)
      throw new IllegalArgumentException("--diagnostics requires a native build");
  }
}
