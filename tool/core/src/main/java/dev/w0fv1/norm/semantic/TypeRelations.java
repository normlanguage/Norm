package dev.w0fv1.norm.semantic;

import java.util.Optional;

public final class TypeRelations {
  private TypeRelations() {}

  public static boolean isAssignable(SemanticType expected, SemanticType actual) {
    if (expected.equals(SemanticType.DYNAMIC) || actual.equals(SemanticType.DYNAMIC)) return true;
    if (actual.equals(SemanticType.NULL)) return expected.isNullable();
    if (expected.equals(SemanticType.NULL)) return actual.equals(SemanticType.NULL);
    if (expected.nonNullable().equals(SemanticType.NUMBER) && NumericTypes.isLeaf(actual)) {
      return expected.isNullable() || !actual.isNullable();
    }
    if (!expected.nonNullable().equals(actual.nonNullable())) return false;
    return expected.isNullable() || !actual.isNullable();
  }

  public static Optional<SemanticType> commonType(SemanticType left, SemanticType right) {
    if (left.equals(right)) return Optional.of(left);
    boolean nullable = left.isNullable() || right.isNullable();
    SemanticType leftBase = left.nonNullable();
    SemanticType rightBase = right.nonNullable();
    SemanticType result;
    if (leftBase.equals(rightBase)) {
      result = leftBase;
    } else if (NumericTypes.isNumber(leftBase) && NumericTypes.isNumber(rightBase)) {
      result = SemanticType.NUMBER;
    } else if (isAssignable(leftBase, rightBase)) {
      result = leftBase;
    } else if (isAssignable(rightBase, leftBase)) {
      result = rightBase;
    } else {
      return Optional.empty();
    }
    return Optional.of(nullable ? result.nullable() : result);
  }

  public static final class DeclarationGraph {
    private final java.util.function.BiPredicate<SemanticType, SemanticType> nominalRelation;

    public DeclarationGraph(
        java.util.function.BiPredicate<SemanticType, SemanticType> nominalRelation) {
      this.nominalRelation = java.util.Objects.requireNonNull(nominalRelation, "nominalRelation");
    }

    public boolean isAssignable(SemanticType expected, SemanticType actual) {
      return TypeRelations.isAssignable(expected, actual) || nominalRelation.test(expected, actual);
    }
  }
}
