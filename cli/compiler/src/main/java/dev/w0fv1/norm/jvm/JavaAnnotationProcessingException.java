package dev.w0fv1.norm.jvm;

import java.io.IOException;

public final class JavaAnnotationProcessingException extends IOException {
  private static final long serialVersionUID = 1L;

  public JavaAnnotationProcessingException(String message) {
    super(message);
  }

  public JavaAnnotationProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
