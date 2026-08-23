package dev.w0fv1.norm.semantic;

public final class TypeRelations {
  private TypeRelations() {}

  public static boolean isAssignable(SemanticType expected, SemanticType actual) {
    return expected.equals(SemanticType.DYNAMIC)
        || actual.equals(SemanticType.DYNAMIC)
        || expected.equals(actual);
  }
}
