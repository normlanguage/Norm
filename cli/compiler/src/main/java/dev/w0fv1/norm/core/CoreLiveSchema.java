package dev.w0fv1.norm.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CoreLiveSchema {
  private final Map<DefinitionOccurrenceId, String> keys;
  private final Map<String, Contract> contracts;
  private final Map<DefinitionOccurrenceId, CoreExecutionLayout> layouts;
  private final Map<String, CoreExecutionLayout> declarationLayouts;
  private final CoreRuntimeSchema runtime;
  private final java.util.Set<DefinitionId> migrations;
  private final String entry;

  private CoreLiveSchema(CoreArtifact artifact) {
    runtime = CoreRuntimeSchema.create(artifact);
    var identity = runtime.identity();
    migrations = CoreStateMigrationProtocol.appliedTypes(artifact, identity);
    var indexedKeys = new HashMap<DefinitionOccurrenceId, String>();
    var indexedContracts = new HashMap<String, Contract>();
    var indexedLayouts = new HashMap<DefinitionOccurrenceId, CoreExecutionLayout>();
    for (var occurrence : artifact.authoring().occurrences()) {
      var owner = occurrence.id().representative();
      var definition = artifact.program().definition(owner).orElseThrow();
      if (!(definition instanceof CoreDefinition.Callable callable)) {
        continue;
      }
      String key =
          occurrence
              .origin()
              .declarationIdentity()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Live execution requires a declaration identity: "
                              + occurrence.origin().definitionName()));
      indexedKeys.put(occurrence.id(), key);
      var layout = new CoreExecutionLayout(callable, occurrence.origin());
      indexedLayouts.put(occurrence.id(), layout);
      var localTypes = new HashMap<String, CoreType>();
      callable
          .locals()
          .forEach(
              local ->
                  localTypes.put(layout.local(local.index()), identity.type(owner, local.type())));
      if (indexedContracts.putIfAbsent(
              key,
              new Contract(
                  Map.copyOf(localTypes),
                  callable.reifiedTypeLocals().stream().map(layout::local).toList()))
          != null) {
        throw new IllegalArgumentException("Ambiguous live declaration identity: " + key);
      }
    }
    keys = Map.copyOf(indexedKeys);
    layouts = Map.copyOf(indexedLayouts);
    var byDeclaration = new HashMap<String, CoreExecutionLayout>();
    keys.forEach((occurrence, key) -> byDeclaration.put(key, layouts.get(occurrence)));
    declarationLayouts = Map.copyOf(byDeclaration);
    contracts = Map.copyOf(indexedContracts);
    entry = key(artifact.entryPoint());
  }

  public static CoreLiveSchema create(CoreArtifact artifact) {
    return new CoreLiveSchema(Objects.requireNonNull(artifact));
  }

  public String key(DefinitionOccurrenceId occurrence) {
    return Objects.requireNonNull(keys.get(occurrence), "live callable identity");
  }

  public CoreExecutionLayout layout(String declaration) {
    return declarationLayouts.get(declaration);
  }

  public CoreExecutionLayout layout(DefinitionOccurrenceId occurrence) {
    return Objects.requireNonNull(layouts.get(occurrence));
  }

  public CoreRuntimeSchema.CallableContract callableContract(DefinitionOccurrenceId occurrence) {
    return runtime.callableContract(occurrence);
  }

  public Map<String, CoreType> localTypes(String declaration) {
    Contract contract = contracts.get(declaration);
    return contract == null ? Map.of() : contract.locals();
  }

  public List<String> reifiedLocals(String declaration) {
    Contract contract = contracts.get(declaration);
    return contract == null ? List.of() : contract.reified();
  }

  public String declarationKey(DefinitionOccurrenceId occurrence) {
    return runtime.identity().key(occurrence);
  }

  public void requireCompatible(CoreLiveSchema candidate) {
    Objects.requireNonNull(candidate);
    if (!entry.equals(candidate.entry))
      throw new IllegalArgumentException("Live update changes the entry declaration");
    runtime.requireCompatible(candidate.runtime, candidate.migrations);
  }

  private record Contract(Map<String, CoreType> locals, List<String> reified) {}
}
