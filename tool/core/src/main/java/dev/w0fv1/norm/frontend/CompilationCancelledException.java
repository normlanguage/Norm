package dev.w0fv1.norm.frontend;

public final class CompilationCancelledException extends RuntimeException {
  @java.io.Serial private static final long serialVersionUID = 1L;

  public CompilationCancelledException() {
    super("compilation was cancelled");
  }
}
