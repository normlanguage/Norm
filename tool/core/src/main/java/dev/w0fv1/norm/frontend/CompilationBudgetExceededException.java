package dev.w0fv1.norm.frontend;

public final class CompilationBudgetExceededException extends RuntimeException {
  @java.io.Serial private static final long serialVersionUID = 1L;

  CompilationBudgetExceededException(String resource, long limit) {
    super("compilation exceeded " + resource + " limit of " + limit);
  }
}
