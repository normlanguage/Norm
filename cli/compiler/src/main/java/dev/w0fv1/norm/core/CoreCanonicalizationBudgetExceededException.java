package dev.w0fv1.norm.core;

public final class CoreCanonicalizationBudgetExceededException extends RuntimeException {
  @java.io.Serial private static final long serialVersionUID = 1L;

  CoreCanonicalizationBudgetExceededException(long limit) {
    super("core canonicalization exceeded search branch limit of " + limit);
  }
}
