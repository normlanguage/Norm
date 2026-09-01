package dev.w0fv1.norm.semantic;

import java.util.Optional;

public final class TypeRelations {
  private TypeRelations() {}

  public static boolean isAssignable(SemanticType expected, SemanticType actual) {
    if (expected.equals(SemanticType.DYNAMIC) || actual.equals(SemanticType.DYNAMIC)) return true;
    if (expected.kind() == SemanticType.Kind.EXISTENTIAL
        || actual.kind() == SemanticType.Kind.EXISTENTIAL) {
      return expected.kind() == SemanticType.Kind.EXISTENTIAL
          && actual.kind() == SemanticType.Kind.EXISTENTIAL;
    }
    if (actual.equals(SemanticType.NULL)) return expected.isNullable();
    if (expected.equals(SemanticType.NULL)) return actual.equals(SemanticType.NULL);
    if (expected.nonNullable().equals(SemanticType.NUMBER) && NumericTypes.isLeaf(actual)) {
      return expected.isNullable() || !actual.isNullable();
    }
    if (actual.isNullable() && !expected.isNullable()) return false;
    SemanticType expectedBase = expected.nonNullable();
    SemanticType actualBase = actual.nonNullable();
    if (expectedBase.equals(SemanticType.ANY)) return expected.isNullable() || !actual.isNullable();
    if (expectedBase.isUnknownFunction() && actualBase.isFunction()) return true;
    if (expectedBase.kind() != actualBase.kind()
        || !expectedBase.identity().equals(actualBase.identity())
        || expectedBase.arguments().size() != actualBase.arguments().size()) {
      return false;
    }
    for (int index = 0; index < expectedBase.arguments().size(); index++) {
      SemanticType expectedArgument = expectedBase.arguments().get(index);
      SemanticType actualArgument = actualBase.arguments().get(index);
      if (expectedArgument.kind() != SemanticType.Kind.EXISTENTIAL
          && !expectedArgument.equals(actualArgument)) {
        return false;
      }
    }
    return true;
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
