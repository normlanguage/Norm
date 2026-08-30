package dev.w0fv1.norm.frontend;

public record CompilationLimits(
    int maximumDocuments,
    long maximumSourceCharacters,
    long maximumWorkUnits,
    long maximumCanonicalSearchBranches) {
  private static final CompilationLimits STANDARD =
      new CompilationLimits(4_096, 16L * 1024 * 1024, 50_000_000, 1_000_000);

  public CompilationLimits {
    if (maximumDocuments < 1)
      throw new IllegalArgumentException("maximumDocuments must be positive");
    if (maximumSourceCharacters < 1) {
      throw new IllegalArgumentException("maximumSourceCharacters must be positive");
    }
    if (maximumWorkUnits < 1)
      throw new IllegalArgumentException("maximumWorkUnits must be positive");
    if (maximumCanonicalSearchBranches < 1) {
      throw new IllegalArgumentException("maximumCanonicalSearchBranches must be positive");
    }
  }

  public static CompilationLimits standard() {
    return STANDARD;
  }
}
