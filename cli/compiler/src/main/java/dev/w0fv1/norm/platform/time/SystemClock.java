package dev.w0fv1.norm.platform.time;

import dev.w0fv1.norm.platform.PlatformInstant;

@FunctionalInterface
public interface SystemClock {
  PlatformInstant now();
}
