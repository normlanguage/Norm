package dev.w0fv1.norm.frontend;

@FunctionalInterface
public interface CancellationToken {
  boolean isCancellationRequested();

  static CancellationToken none() {
    return () -> false;
  }
}
