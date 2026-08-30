package dev.w0fv1.norm.platform;

public record PlatformInstant(long epochSecond, int nanosecond) {
  public PlatformInstant {
    if (nanosecond < 0 || nanosecond > 999_999_999) {
      throw new IllegalArgumentException("nanosecond must be within 0..999999999");
    }
  }
}
