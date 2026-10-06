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
  private final List<String> dependencyOrder;
  private final Map<String, SemanticType> variableTypes;
  private final Set<String> variables;
  private final Map<String, List<SemanticType>> constraints = new LinkedHashMap<>();
  private final Map<String, SemanticType> upperBounds = new LinkedHashMap<>();
  private final TypeRelations.DeclarationGraph relations;

  public TypeConstraintSolver(Iterable<SemanticType> variables) {
    this(variables, new TypeRelations.DeclarationGraph(ignored -> List.of()));
  }

  public TypeConstraintSolver(
      Iterable<SemanticType> variables, TypeRelations.DeclarationGraph relations) {
    this.relations = Objects.requireNonNull(relations, "relations");
    Set<String> identities = new LinkedHashSet<>();
    var ordered = new LinkedHashMap<String, SemanticType>();
    for (SemanticType variable : variables) {
      if (variable.kind() != SemanticType.Kind.TYPE_PARAMETER) {
        throw new IllegalArgumentException("inference variables must be type parameters");
      }
      identities.add(variable.identity());
      collectVariables(variable, relations, ordered, new java.util.HashSet<>());
      relations
          .upperBound(variable)
          .ifPresent(bound -> upperBounds.put(variable.identity(), bound));
    }
    this.orderedVariables = List.copyOf(identities);
    this.variables = Set.copyOf(identities);
    this.dependencyOrder = ordered.keySet().stream().filter(identities::contains).toList();
    this.variableTypes = Map.copyOf(ordered);
  }

  public static TypeConstraintSolver forPattern(
      SemanticType pattern, TypeRelations.DeclarationGraph relations) {
    var variables = new LinkedHashMap<String, SemanticType>();
    collectVariables(pattern, relations, variables, new java.util.HashSet<>());
    return new TypeConstraintSolver(variables.values(), relations);
  }

  private static void collectVariables(
      SemanticType type,
      TypeRelations.DeclarationGraph relations,
      Map<String, SemanticType> variables,
      Set<String> visited) {
    type.arguments().forEach(argument -> collectVariables(argument, relations, variables, visited));
    if (type.kind() != SemanticType.Kind.TYPE_PARAMETER || !visited.add(type.identity())) return;
    relations
        .upperBound(type)
        .ifPresent(bound -> collectVariables(bound, relations, variables, visited));
    variables.put(type.identity(), type.nonNullable());
  }

  public void constrain(SemanticType pattern, SemanticType actual) {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(actual, "actual");
    var pending = new java.util.ArrayDeque<Constraint>();
    var visited = new java.util.HashSet<Constraint>();
    pending.add(new Constraint(pattern, actual, Set.of()));
    while (!pending.isEmpty()) {
      var constraint = pending.removeFirst();
      if (!visited.add(constraint)) continue;
      pattern = constraint.pattern();
      actual = constraint.actual();
      if (pattern.equals(actual)
          || actual.equals(SemanticType.NULL)
          || actual.equals(SemanticType.DYNAMIC)
          || actual.isReference()) continue;
      if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER
          && variables.contains(pattern.identity())) {
        SemanticType inferred = pattern.isNullable() ? actual.nonNullable() : actual;
        var values = constraints.computeIfAbsent(pattern.identity(), ignored -> new ArrayList<>());
        if (!values.contains(inferred)) values.add(inferred);
        var bound = upperBounds.get(pattern.identity());
        if (bound != null && !constraint.expandedVariables().contains(pattern.identity())) {
          var expanded = new LinkedHashSet<>(constraint.expandedVariables());
          expanded.add(pattern.identity());
          pending.addLast(new Constraint(bound, inferred, Set.copyOf(expanded)));
        }
        continue;
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
        continue;
      }
      for (int index = 0; index < pattern.arguments().size(); index++) {
        pending.addLast(
            new Constraint(
                pattern.arguments().get(index),
                actual.arguments().get(index),
                constraint.expandedVariables()));
      }
    }
  }

  public Solution solve() {
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    List<Conflict> conflicts = new ArrayList<>();
    for (String variable : dependencyOrder) {
      List<SemanticType> inferred = constraints.getOrDefault(variable, List.of());
      if (inferred.isEmpty()) {
        missing.add(variable);
        continue;
      }
      SemanticType merged = inferred.getFirst();
      for (int index = 1; index < inferred.size(); index++) {
        SemanticType candidate = inferred.get(index);
        SemanticType common =
            relations
                .commonType(
                    merged,
                    candidate,
                    relations.upperBound(variableTypes.get(variable), substitutions).orElse(null))
                .orElse(null);
        if (common == null) {
          conflicts.add(new Conflict(variable, merged, candidate));
          break;
        }
        merged = common;
      }
      substitutions.put(variable, merged);
    }
    return new Solution(
        substitutions, orderedVariables.stream().filter(missing::contains).toList(), conflicts);
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

  private record Constraint(
      SemanticType pattern, SemanticType actual, Set<String> expandedVariables) {}
}
