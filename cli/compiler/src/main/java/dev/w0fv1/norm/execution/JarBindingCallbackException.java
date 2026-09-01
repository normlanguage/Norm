package dev.w0fv1.norm.execution;

import java.util.Objects;

public final class JarBindingCallbackException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final RuntimeException failure;

  public JarBindingCallbackException(RuntimeException failure) {
    super(Objects.requireNonNull(failure, "failure"));
    this.failure = failure;
  }

  public RuntimeException failure() {
    return failure;
  }
}
