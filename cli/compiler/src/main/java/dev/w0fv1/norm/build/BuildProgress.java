package dev.w0fv1.norm.build;

import java.util.Objects;

public record BuildProgress(Stage stage, String message) {
  public BuildProgress {
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(message, "message");
  }

  public enum Stage {
    COMPILATION,
    JVM_PACKAGING,
    NATIVE_BUILD,
    COMPLETE
  }
}
