package dev.w0fv1.norm.execution;

import java.util.Objects;

public final class JarBindingInvocationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final Throwable failure;

  public JarBindingInvocationException(String message, Throwable failure) {
    super(message, Objects.requireNonNull(failure, "failure"));
    this.failure = failure;
  }

  public Throwable failure() {
    return failure;
  }
}
