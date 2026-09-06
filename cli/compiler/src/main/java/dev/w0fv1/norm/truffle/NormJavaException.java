package dev.w0fv1.norm.truffle;

final class NormJavaException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  final transient RuntimeValues.ObjectValue value;

  NormJavaException(String message, RuntimeValues.ObjectValue value) {
    super(message);
    this.value = java.util.Objects.requireNonNull(value, "value");
  }
}
