package dev.w0fv1.norm.frontend;

public record CompilerSessionCapacity(int parsedDocuments, int compilationUnits) {
  private static final CompilerSessionCapacity STANDARD = new CompilerSessionCapacity(512, 64);

  public CompilerSessionCapacity {
    if (parsedDocuments < 1) throw new IllegalArgumentException("parsedDocuments must be positive");
    if (compilationUnits < 1)
      throw new IllegalArgumentException("compilationUnits must be positive");
  }

  public static CompilerSessionCapacity standard() {
    return STANDARD;
  }
}
