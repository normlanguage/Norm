package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CoreDependencyIndex<T> {
  private final Map<T, Set<T>> dependencies;
  private final Map<T, Set<T>> dependents;

  private CoreDependencyIndex(Map<T, Set<T>> dependencies) {
    this.dependencies = copy(dependencies);
    Map<T, Set<T>> reverse = new LinkedHashMap<>();
    dependencies.forEach(
        (definition, targets) -> {
          reverse.computeIfAbsent(definition, ignored -> new LinkedHashSet<>());
          targets.forEach(
              target ->
                  reverse
                      .computeIfAbsent(target, ignored -> new LinkedHashSet<>())
                      .add(definition));
        });
    this.dependents = copy(reverse);
  }

  public static CoreDependencyIndex<DefinitionId> create(CoreProgram program) {
    Objects.requireNonNull(program, "program");
    Map<DefinitionId, Set<DefinitionId>> direct = new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.definitions()) {
      LinkedHashSet<DefinitionId> targets = new LinkedHashSet<>();
      for (CoreDefinitionLink link : CoreTree.links(record.definition())) {
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("dependency index requires resolved core");
        }
        targets.add(program.resolve(record.id(), reference));
      }
      direct.put(record.id(), targets);
    }
    return new CoreDependencyIndex<>(direct);
  }

  public static CoreDependencyIndex<DefinitionOccurrenceId> create(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    Map<DefinitionId, Set<DefinitionOccurrenceId>> represented = new LinkedHashMap<>();
    for (var occurrence : artifact.authoring().occurrences()) {
      occurrence
          .representedDefinitions()
          .forEach(
              definition ->
                  represented
                      .computeIfAbsent(definition, ignored -> new LinkedHashSet<>())
                      .add(occurrence.id()));
    }
    Map<DefinitionOccurrenceId, Set<DefinitionOccurrenceId>> direct = new LinkedHashMap<>();
    for (var occurrence : artifact.authoring().occurrences()) {
      Set<DefinitionOccurrenceId> targets = new LinkedHashSet<>();
      new CoreWalker() {
        @Override
        protected void visitReferenceDependency(
            int nodeIndex, CoreDependency.Kind kind, CoreDefinitionLink link) {
          targets.add(artifact.authoring().target(occurrence.id(), nodeIndex));
        }

        @Override
        protected void visitDependency(CoreDependency.Kind kind, CoreDefinitionLink link) {
          if (!(link instanceof DefinitionReference reference)) {
            throw new IllegalArgumentException("dependency index requires resolved core");
          }
          var definition = artifact.program().resolve(occurrence.id().representative(), reference);
          targets.addAll(represented.getOrDefault(definition, Set.of()));
        }
      }.walk(artifact.program().definition(occurrence.id().representative()).orElseThrow());
      direct.put(occurrence.id(), targets);
    }
    for (var binding : artifact.namespace().bindings()) {
      binding.shape().parameters().stream()
          .flatMap(parameter -> parameter.defaultValue().stream())
          .map(value -> ((CoreDefaultArgument.Resolved) value).occurrence())
          .forEach(direct.get(binding.occurrence())::add);
    }
    return new CoreDependencyIndex<>(direct);
  }

  public Set<T> dependenciesOf(T definition) {
    return dependencies.getOrDefault(Objects.requireNonNull(definition, "definition"), Set.of());
  }

  public Set<T> directDependentsOf(T definition) {
    return dependents.getOrDefault(Objects.requireNonNull(definition, "definition"), Set.of());
  }

  public Set<T> transitiveDependentsOf(Set<T> changed) {
    ArrayDeque<T> pending = new ArrayDeque<>(changed);
    LinkedHashSet<T> result = new LinkedHashSet<>();
    while (!pending.isEmpty()) {
      T definition = pending.removeFirst();
      for (T dependent : directDependentsOf(definition)) {
        if (result.add(dependent)) pending.addLast(dependent);
      }
    }
    return Set.copyOf(result);
  }

  private static <T> Map<T, Set<T>> copy(Map<T, Set<T>> values) {
    Map<T, Set<T>> result = new LinkedHashMap<>();
    values.forEach((definition, targets) -> result.put(definition, Set.copyOf(targets)));
    return Map.copyOf(result);
  }
}
