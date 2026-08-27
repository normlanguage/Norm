package dev.w0fv1.norm.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record CoreDefinitionOccurrence(
    DefinitionOccurrenceId id,
    Set<DefinitionId> representedDefinitions,
    CoreDefinitionRole role,
    CoreDefinitionOrigin origin,
    Map<Integer, DefinitionOccurrenceId> references) {
  public CoreDefinitionOccurrence {
    Objects.requireNonNull(id, "id");
    representedDefinitions =
        java.util.Collections.unmodifiableSet(
            new TreeSet<>(
                Objects.requireNonNull(representedDefinitions, "representedDefinitions")));
    if (!representedDefinitions.contains(id.representative())) {
      throw new IllegalArgumentException("occurrence representative is outside its orbit");
    }
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(origin, "origin");
    Map<Integer, DefinitionOccurrenceId> stableReferences = new LinkedHashMap<>();
    Objects.requireNonNull(references, "references").entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              if (entry.getKey() < 0) {
                throw new IllegalArgumentException("reference node index must not be negative");
              }
              stableReferences.put(entry.getKey(), Objects.requireNonNull(entry.getValue()));
            });
    references = Map.copyOf(stableReferences);
  }
}
