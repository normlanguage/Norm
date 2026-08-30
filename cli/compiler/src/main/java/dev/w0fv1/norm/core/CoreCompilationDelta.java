package dev.w0fv1.norm.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record CoreCompilationDelta(
    Set<DefinitionId> added, Set<DefinitionId> reused, Set<DefinitionId> detached) {
  public CoreCompilationDelta {
    added = Set.copyOf(added);
    reused = Set.copyOf(reused);
    detached = Set.copyOf(detached);
  }

  public static CoreCompilationDelta initial(CoreProgram program) {
    Set<DefinitionId> definitions = definitionIds(program);
    return new CoreCompilationDelta(definitions, Set.of(), Set.of());
  }

  public static CoreCompilationDelta between(CoreProgram previous, CoreProgram current) {
    Objects.requireNonNull(previous, "previous");
    Objects.requireNonNull(current, "current");
    Set<DefinitionId> before = definitionIds(previous);
    Set<DefinitionId> after = definitionIds(current);
    LinkedHashSet<DefinitionId> added = new LinkedHashSet<>(after);
    added.removeAll(before);
    LinkedHashSet<DefinitionId> reused = new LinkedHashSet<>(after);
    reused.retainAll(before);
    LinkedHashSet<DefinitionId> detached = new LinkedHashSet<>(before);
    detached.removeAll(after);
    return new CoreCompilationDelta(added, reused, detached);
  }

  private static Set<DefinitionId> definitionIds(CoreProgram program) {
    return program.definitions().stream()
        .map(CoreDefinitionRecord::id)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
