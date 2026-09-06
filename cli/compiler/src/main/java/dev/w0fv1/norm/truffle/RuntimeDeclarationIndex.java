package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreAuthoringMap;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RuntimeDeclarationIndex {
  private final Map<DefinitionOccurrenceId, String> names;
  private final Map<DefinitionId, List<DefinitionOccurrenceId>> definitions;

  private RuntimeDeclarationIndex(
      Map<DefinitionOccurrenceId, String> names,
      Map<DefinitionId, List<DefinitionOccurrenceId>> definitions) {
    this.names = Map.copyOf(names);
    this.definitions = Map.copyOf(definitions);
  }

  static RuntimeDeclarationIndex from(CoreAuthoringMap authoring) {
    Map<DefinitionOccurrenceId, String> names = new HashMap<>();
    Map<DefinitionId, List<DefinitionOccurrenceId>> definitions = new HashMap<>();
    for (var occurrence : authoring.occurrences()) {
      names.put(occurrence.id(), occurrence.origin().definitionName());
      for (var definition : occurrence.representedDefinitions()) {
        definitions.computeIfAbsent(definition, ignored -> new ArrayList<>()).add(occurrence.id());
      }
    }
    definitions.replaceAll((id, occurrences) -> List.copyOf(occurrences));
    return new RuntimeDeclarationIndex(names, definitions);
  }

  String name(DefinitionOccurrenceId occurrence) {
    String name = names.get(occurrence);
    if (name == null) throw new IllegalArgumentException("definition occurrence is absent");
    return name;
  }

  List<DefinitionOccurrenceId> occurrences(DefinitionId definition) {
    return definitions.getOrDefault(java.util.Objects.requireNonNull(definition), List.of());
  }
}
