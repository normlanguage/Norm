package dev.w0fv1.norm.execution;

public final class JarBindingRuntimeException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final RuntimeErrorCode code;

  public JarBindingRuntimeException(String message) {
    this(RuntimeErrorCode.JAR_BINDING, message);
  }

  public JarBindingRuntimeException(RuntimeErrorCode code, String message) {
    super(message);
    this.code = java.util.Objects.requireNonNull(code);
  }

  public JarBindingRuntimeException(String message, Throwable cause) {
    super(message, cause);
    code = RuntimeErrorCode.JAR_BINDING;
  }

  public RuntimeErrorCode code() {
    return code;
  }
}
