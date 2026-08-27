package dev.w0fv1.norm.execution;

@FunctionalInterface
public interface SystemClock {
  PlatformInstant now();
}
