package dev.w0fv1.norm.frontend;

public final class CompilationInfrastructureException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  CompilationInfrastructureException(String message, Throwable cause) {
    super(message, cause);
  }
}
