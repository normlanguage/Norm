package dev.w0fv1.norm.frontend;

public record CompilationControl(CancellationToken cancellation, CompilationLimits limits) {
  private static final CompilationControl STANDARD =
      new CompilationControl(CancellationToken.none(), CompilationLimits.standard());

  public CompilationControl {
    java.util.Objects.requireNonNull(cancellation, "cancellation");
    java.util.Objects.requireNonNull(limits, "limits");
  }

  public static CompilationControl standard() {
    return STANDARD;
  }

  CompilationGuard begin() {
    return new CompilationGuard(cancellation, limits);
  }
}
