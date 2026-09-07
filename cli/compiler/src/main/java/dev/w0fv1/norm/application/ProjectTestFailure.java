package dev.w0fv1.norm.application;

import java.util.Objects;

public record ProjectTestFailure(String test, String message) {
  public ProjectTestFailure {
    Objects.requireNonNull(test, "test");
    Objects.requireNonNull(message, "message");
  }
}
