package dev.w0fv1.norm.execution;

public record PlatformDuration(long seconds, int nanoseconds) {
  public PlatformDuration {
    if (seconds < 0) throw new IllegalArgumentException("seconds must not be negative");
    if (nanoseconds < 0 || nanoseconds > 999_999_999) {
      throw new IllegalArgumentException("nanoseconds must be within 0..999999999");
    }
  }

  public boolean isZero() {
    return seconds == 0 && nanoseconds == 0;
  }
}
