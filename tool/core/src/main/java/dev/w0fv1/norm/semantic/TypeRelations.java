package dev.w0fv1.norm.semantic;

public final class TypeRelations {
  private TypeRelations() {}

  public static boolean isAssignable(SemanticType expected, SemanticType actual) {
    if (expected.equals(SemanticType.DYNAMIC) || actual.equals(SemanticType.DYNAMIC)) return true;
    if (actual.equals(SemanticType.NULL)) return expected.isNullable();
    if (expected.equals(SemanticType.NULL)) return actual.equals(SemanticType.NULL);
    if (!expected.nonNullable().equals(actual.nonNullable())) return false;
    return expected.isNullable() || !actual.isNullable();
  }
}
