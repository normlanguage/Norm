package dev.w0fv1.norm.core;

public final class CoreCanonicalizationCancelledException extends RuntimeException {
  @java.io.Serial private static final long serialVersionUID = 1L;

  CoreCanonicalizationCancelledException() {
    super("core canonicalization was cancelled");
  }
}
