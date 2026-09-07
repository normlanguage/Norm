package dev.w0fv1.norm.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TypeConstraintSolver {
  private final List<String> orderedVariables;
  private final Set<String> variables;
  private final Map<String, List<SemanticType>> constraints = new LinkedHashMap<>();
  private final TypeRelations.DeclarationGraph relations;

  public TypeConstraintSolver(Iterable<SemanticType> variables) {
    this(variables, new TypeRelations.DeclarationGraph(ignored -> List.of()));
  }

  public TypeConstraintSolver(
      Iterable<SemanticType> variables, TypeRelations.DeclarationGraph relations) {
    this.relations = Objects.requireNonNull(relations, "relations");
    Set<String> identities = new LinkedHashSet<>();
    for (SemanticType variable : variables) {
      if (variable.kind() != SemanticType.Kind.TYPE_PARAMETER) {
        throw new IllegalArgumentException("inference variables must be type parameters");
      }
      identities.add(variable.identity());
    }
    this.orderedVariables = List.copyOf(identities);
    this.variables = Set.copyOf(identities);
  }

  public void constrain(SemanticType pattern, SemanticType actual) {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(actual, "actual");
    if (actual.equals(SemanticType.NULL)
        || actual.equals(SemanticType.DYNAMIC)
        || actual.isReference()) return;
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER
        && variables.contains(pattern.identity())) {
      SemanticType inferred = pattern.isNullable() ? actual.nonNullable() : actual;
      constraints.computeIfAbsent(pattern.identity(), ignored -> new ArrayList<>()).add(inferred);
      return;
    }
    if (!pattern.identity().equals(actual.identity())) {
      String patternIdentity = pattern.identity();
      String actualIdentity = actual.identity();
      actual =
          relations.views(actual).stream()
              .filter(view -> view.identity().equals(patternIdentity))
              .findFirst()
              .orElse(actual);
      if (!pattern.identity().equals(actual.identity())) {
        pattern =
            relations.views(pattern).stream()
                .filter(view -> view.identity().equals(actualIdentity))
                .findFirst()
                .orElse(pattern);
      }
    }
    if (!pattern.nonNullable().identity().equals(actual.nonNullable().identity())
        || pattern.arguments().size() != actual.arguments().size()) {
      return;
    }
    for (int index = 0; index < pattern.arguments().size(); index++) {
      constrain(pattern.arguments().get(index), actual.arguments().get(index));
    }
  }

  public Solution solve() {
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    List<Conflict> conflicts = new ArrayList<>();
    for (String variable : orderedVariables) {
      List<SemanticType> inferred = constraints.getOrDefault(variable, List.of());
      if (inferred.isEmpty()) {
        missing.add(variable);
        continue;
      }
      SemanticType merged = inferred.getFirst();
      for (int index = 1; index < inferred.size(); index++) {
        SemanticType candidate = inferred.get(index);
        SemanticType common = relations.commonType(merged, candidate).orElse(null);
        if (common == null) {
          conflicts.add(new Conflict(variable, merged, candidate));
          break;
        }
        merged = common;
      }
      substitutions.put(variable, merged);
    }
    return new Solution(substitutions, missing, conflicts);
  }

  public record Solution(
      Map<String, SemanticType> substitutions, List<String> missing, List<Conflict> conflicts) {
    public Solution {
      substitutions = Map.copyOf(substitutions);
      missing = List.copyOf(missing);
      conflicts = List.copyOf(conflicts);
    }
  }

  public record Conflict(String variable, SemanticType first, SemanticType second) {}
}
