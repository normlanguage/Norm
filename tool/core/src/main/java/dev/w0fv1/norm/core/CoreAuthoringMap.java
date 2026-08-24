package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CoreAuthoringMap {
  private final List<CoreDefinitionOccurrence> occurrences;
  private final Map<DefinitionOccurrenceId, CoreDefinitionOccurrence> occurrencesById;
  private final DefinitionOccurrenceId entryPoint;

  public CoreAuthoringMap(
      List<CoreDefinitionOccurrence> occurrences, DefinitionOccurrenceId entryPoint) {
    this.occurrences =
        Objects.requireNonNull(occurrences, "occurrences").stream()
            .sorted(java.util.Comparator.comparing(CoreDefinitionOccurrence::id))
            .toList();
    validateCanonicalOrdinals(this.occurrences);
    Map<DefinitionOccurrenceId, CoreDefinitionOccurrence> indexed = new LinkedHashMap<>();
    for (CoreDefinitionOccurrence occurrence : this.occurrences) {
      if (indexed.putIfAbsent(occurrence.id(), occurrence) != null) {
        throw new IllegalArgumentException("duplicate definition occurrence " + occurrence.id());
      }
    }
    this.occurrencesById = Map.copyOf(indexed);
    this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
    if (!occurrencesById.containsKey(entryPoint)) {
      throw new IllegalArgumentException("entry occurrence is absent");
    }
    for (CoreDefinitionOccurrence occurrence : this.occurrences) {
      for (DefinitionOccurrenceId target : occurrence.references().values()) {
        if (!occurrencesById.containsKey(target)) {
          throw new IllegalArgumentException("reference target occurrence is absent: " + target);
        }
      }
    }
  }

  public static Allocation allocate(List<Seed> seeds, int entryIndex) {
    List<Seed> definitions = List.copyOf(seeds);
    if (definitions.isEmpty()) {
      throw new IllegalArgumentException("authoring map requires definition occurrences");
    }
    if (entryIndex < 0 || entryIndex >= definitions.size()) {
      throw new IllegalArgumentException("entry declaration is outside the occurrence table");
    }
    List<DefinitionOccurrenceId> ids =
        new ArrayList<>(java.util.Collections.nCopies(definitions.size(), null));
    Map<DefinitionId, List<Integer>> declarationsByDefinition = new java.util.TreeMap<>();
    for (int index = 0; index < definitions.size(); index++) {
      declarationsByDefinition
          .computeIfAbsent(definitions.get(index).representative(), ignored -> new ArrayList<>())
          .add(index);
    }
    declarationsByDefinition.forEach(
        (definition, declarations) -> {
          List<Integer> sorted =
              declarations.stream()
                  .sorted(java.util.Comparator.comparing(index -> definitions.get(index).origin()))
                  .toList();
          for (int ordinal = 0; ordinal < sorted.size(); ordinal++) {
            if (ordinal > 0
                && definitions
                        .get(sorted.get(ordinal - 1))
                        .origin()
                        .compareTo(definitions.get(sorted.get(ordinal)).origin())
                    == 0) {
              throw new IllegalArgumentException("definition occurrences have duplicate origins");
            }
            ids.set(sorted.get(ordinal), new DefinitionOccurrenceId(definition, ordinal));
          }
        });
    List<CoreDefinitionOccurrence> occurrences = new ArrayList<>();
    for (int index = 0; index < definitions.size(); index++) {
      Seed seed = definitions.get(index);
      Map<Integer, DefinitionOccurrenceId> references = new LinkedHashMap<>();
      seed.referenceTargets()
          .forEach(
              (node, target) -> {
                if (target < 0 || target >= ids.size()) {
                  throw new IllegalArgumentException(
                      "reference target is outside the occurrence table");
                }
                references.put(node, ids.get(target));
              });
      occurrences.add(
          new CoreDefinitionOccurrence(
              ids.get(index), seed.representedDefinitions(), seed.origin(), references));
    }
    List<DefinitionOccurrenceId> stableIds = List.copyOf(ids);
    return new Allocation(new CoreAuthoringMap(occurrences, stableIds.get(entryIndex)), stableIds);
  }

  public List<CoreDefinitionOccurrence> occurrences() {
    return occurrences;
  }

  public DefinitionOccurrenceId entryPoint() {
    return entryPoint;
  }

  public CoreAuthoringMap withEntryPoint(DefinitionOccurrenceId entryPoint) {
    return new CoreAuthoringMap(occurrences, entryPoint);
  }

  public Optional<CoreDefinitionOccurrence> occurrence(DefinitionOccurrenceId id) {
    return Optional.ofNullable(occurrencesById.get(Objects.requireNonNull(id, "id")));
  }

  public List<CoreDefinitionOccurrence> occurrences(DefinitionId definition) {
    Objects.requireNonNull(definition, "definition");
    return occurrences.stream()
        .filter(occurrence -> occurrence.representedDefinitions().contains(definition))
        .toList();
  }

  public CoreDefinitionOrigin origin(DefinitionOccurrenceId occurrence) {
    return occurrence(occurrence)
        .orElseThrow(() -> new IllegalArgumentException("definition occurrence is absent"))
        .origin();
  }

  public Optional<SourceSpan> span(DefinitionOccurrenceId occurrence, int nodeIndex) {
    return origin(occurrence).span(nodeIndex);
  }

  public DefinitionOccurrenceId target(DefinitionOccurrenceId caller, int nodeIndex) {
    CoreDefinitionOccurrence occurrence =
        occurrence(caller)
            .orElseThrow(() -> new IllegalArgumentException("caller occurrence is absent"));
    DefinitionOccurrenceId target = occurrence.references().get(nodeIndex);
    if (target == null) {
      throw new IllegalArgumentException("authoring reference is absent at node " + nodeIndex);
    }
    return target;
  }

  private static void validateCanonicalOrdinals(List<CoreDefinitionOccurrence> occurrences) {
    Map<DefinitionId, List<CoreDefinitionOccurrence>> grouped = new java.util.TreeMap<>();
    occurrences.forEach(
        occurrence ->
            grouped
                .computeIfAbsent(occurrence.id().representative(), ignored -> new ArrayList<>())
                .add(occurrence));
    grouped.forEach(
        (definition, values) -> {
          List<CoreDefinitionOccurrence> sorted =
              values.stream()
                  .sorted(java.util.Comparator.comparing(CoreDefinitionOccurrence::origin))
                  .toList();
          Set<DefinitionId> orbit = sorted.getFirst().representedDefinitions();
          for (int ordinal = 0; ordinal < sorted.size(); ordinal++) {
            CoreDefinitionOccurrence occurrence = sorted.get(ordinal);
            if (occurrence.id().ordinal() != ordinal) {
              throw new IllegalArgumentException(
                  "definition occurrence ordinals are not canonical");
            }
            if (!occurrence.representedDefinitions().equals(orbit)) {
              throw new IllegalArgumentException(
                  "definition occurrences for one representative require one orbit");
            }
            if (ordinal > 0
                && sorted.get(ordinal - 1).origin().compareTo(occurrence.origin()) == 0) {
              throw new IllegalArgumentException("definition occurrences have duplicate origins");
            }
          }
        });
  }

  public record Seed(
      DefinitionId representative,
      Set<DefinitionId> representedDefinitions,
      CoreDefinitionOrigin origin,
      Map<Integer, Integer> referenceTargets) {
    public Seed {
      Objects.requireNonNull(representative, "representative");
      representedDefinitions = Set.copyOf(representedDefinitions);
      if (!representedDefinitions.contains(representative)) {
        throw new IllegalArgumentException("occurrence representative is outside its orbit");
      }
      Objects.requireNonNull(origin, "origin");
      referenceTargets = Map.copyOf(referenceTargets);
    }
  }

  public record Allocation(CoreAuthoringMap authoring, List<DefinitionOccurrenceId> occurrenceIds) {
    public Allocation {
      Objects.requireNonNull(authoring, "authoring");
      occurrenceIds = List.copyOf(occurrenceIds);
    }
  }
}
