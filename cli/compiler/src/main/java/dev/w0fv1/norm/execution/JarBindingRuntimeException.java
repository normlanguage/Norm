package dev.w0fv1.norm.execution;

public final class JarBindingRuntimeException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public JarBindingRuntimeException(String message) {
    super(message);
  }

  public JarBindingRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
