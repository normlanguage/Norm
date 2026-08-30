package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CoreDependencyIndex {
  private final Map<DefinitionId, Set<DefinitionId>> dependencies;
  private final Map<DefinitionId, Set<DefinitionId>> dependents;

  private CoreDependencyIndex(
      Map<DefinitionId, Set<DefinitionId>> dependencies,
      Map<DefinitionId, Set<DefinitionId>> dependents) {
    this.dependencies = dependencies;
    this.dependents = dependents;
  }

  public static CoreDependencyIndex create(CoreProgram program) {
    Objects.requireNonNull(program, "program");
    Map<DefinitionId, Set<DefinitionId>> direct = new LinkedHashMap<>();
    Map<DefinitionId, Set<DefinitionId>> reverse = new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.definitions()) {
      LinkedHashSet<DefinitionId> targets = new LinkedHashSet<>();
      for (CoreDefinitionLink link : CoreTree.links(record.definition())) {
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("dependency index requires resolved core");
        }
        targets.add(program.resolve(record.id(), reference));
      }
      direct.put(record.id(), Set.copyOf(targets));
      targets.forEach(
          target ->
              reverse.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(record.id()));
    }
    direct.keySet().forEach(definition -> reverse.putIfAbsent(definition, Set.of()));
    return new CoreDependencyIndex(copy(direct), copy(reverse));
  }

  public Set<DefinitionId> dependenciesOf(DefinitionId definition) {
    return dependencies.getOrDefault(Objects.requireNonNull(definition, "definition"), Set.of());
  }

  public Set<DefinitionId> directDependentsOf(DefinitionId definition) {
    return dependents.getOrDefault(Objects.requireNonNull(definition, "definition"), Set.of());
  }

  public Set<DefinitionId> transitiveDependentsOf(Set<DefinitionId> changed) {
    ArrayDeque<DefinitionId> pending = new ArrayDeque<>(changed);
    LinkedHashSet<DefinitionId> result = new LinkedHashSet<>();
    while (!pending.isEmpty()) {
      DefinitionId definition = pending.removeFirst();
      for (DefinitionId dependent : directDependentsOf(definition)) {
        if (result.add(dependent)) pending.addLast(dependent);
      }
    }
    return Set.copyOf(result);
  }

  private static Map<DefinitionId, Set<DefinitionId>> copy(
      Map<DefinitionId, ? extends Set<DefinitionId>> values) {
    Map<DefinitionId, Set<DefinitionId>> result = new LinkedHashMap<>();
    values.forEach((definition, targets) -> result.put(definition, Set.copyOf(targets)));
    return Map.copyOf(result);
  }
}
