package dev.w0fv1.norm.execution;

public record JarBindingDuration(long seconds, int nanoseconds) {
  public JarBindingDuration {
    if (nanoseconds < 0 || nanoseconds > 999_999_999) {
      throw new IllegalArgumentException("nanoseconds must be within 0..999999999");
    }
  }
}
