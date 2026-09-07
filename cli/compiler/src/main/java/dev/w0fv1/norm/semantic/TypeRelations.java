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
    private final java.util.function.Function<SemanticType, java.util.List<SemanticType>> parents;

    public DeclarationGraph(
        java.util.function.Function<SemanticType, java.util.List<SemanticType>> parents) {
      this.parents = java.util.Objects.requireNonNull(parents, "parents");
    }

    public boolean isAssignable(SemanticType expected, SemanticType actual) {
      if (TypeRelations.isAssignable(expected, actual)) return true;
      if (actual.isNullable() && !expected.isNullable()) return false;
      return views(actual).stream().anyMatch(view -> TypeRelations.isAssignable(expected, view));
    }

    public java.util.List<SemanticType> views(SemanticType type) {
      var result = new java.util.LinkedHashMap<String, SemanticType>();
      var pending = new java.util.ArrayDeque<SemanticType>();
      pending.add(type.nonNullable());
      while (!pending.isEmpty()) {
        SemanticType current = pending.removeFirst();
        if (result.putIfAbsent(current.identity(), current) != null) continue;
        pending.addAll(parents.apply(current));
      }
      return result.values().stream()
          .map(view -> type.isNullable() ? view.nullable() : view)
          .toList();
    }

    public boolean mayContainNull(SemanticType type) {
      if (!type.mayContainNull()) return false;
      if (type.isNullable() || type.kind() != SemanticType.Kind.TYPE_PARAMETER) return true;
      return views(type).stream().allMatch(SemanticType::mayContainNull);
    }

    public Optional<SemanticType> commonType(SemanticType left, SemanticType right) {
      Optional<SemanticType> direct = TypeRelations.commonType(left, right);
      if (direct.isPresent()) return direct;
      SemanticType first = left.nonNullable();
      SemanticType second = right.nonNullable();
      var shared = views(first).stream().filter(view -> isAssignable(view, second)).toList();
      var specific =
          shared.stream()
              .filter(
                  candidate ->
                      shared.stream()
                          .noneMatch(
                              other ->
                                  !candidate.equals(other)
                                      && isAssignable(candidate, other)
                                      && !isAssignable(other, candidate)))
              .toList();
      if (specific.size() != 1) return Optional.empty();
      SemanticType result = specific.getFirst();
      return Optional.of(left.isNullable() || right.isNullable() ? result.nullable() : result);
    }
  }
}
