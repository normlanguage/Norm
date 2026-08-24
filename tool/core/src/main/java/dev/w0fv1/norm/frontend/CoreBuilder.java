package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.core.CoreAuthoringMap;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBuildReport;
import dev.w0fv1.norm.core.CoreCanonicalizer;
import dev.w0fv1.norm.core.CoreCompilation;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.core.CoreDependencyIndex;
import dev.w0fv1.norm.core.CoreNamespace;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.PutResult;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CoreBuilder {
  private final BoundProgram program;
  private final Set<DocumentId> exportedSources;
  private final Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates;
  private final DefinitionStore store;

  CoreBuilder(
      BoundProgram program,
      Set<DocumentId> exportedSources,
      Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates,
      DefinitionStore store) {
    this.program = Objects.requireNonNull(program, "program");
    this.exportedSources = Set.copyOf(exportedSources);
    this.sourceCoordinates = Map.copyOf(sourceCoordinates);
    this.store = Objects.requireNonNull(store, "store");
  }

  CoreCompilation build() {
    BoundCoreConverter.Result converted =
        new BoundCoreConverter(program, sourceCoordinates).convert();
    CoreCanonicalizer.Result canonical =
        new CoreCanonicalizer()
            .canonicalize(
                converted.declarations().stream()
                    .map(BoundCoreConverter.Declaration::definition)
                    .toList());
    CoreProgram coreProgram = new CoreProgram(canonical.groups());
    StoreCounts counts = store(canonical.groups());
    List<CoreAuthoringMap.Seed> seeds = new ArrayList<>();
    for (int declaration = 0; declaration < converted.declarations().size(); declaration++) {
      BoundCoreConverter.Declaration value = converted.declarations().get(declaration);
      seeds.add(
          new CoreAuthoringMap.Seed(
              canonical.definitionIds().get(declaration),
              canonical.definitionOrbits().get(declaration),
              value.origin(),
              value.referenceTargets()));
    }
    CoreAuthoringMap.Allocation allocation =
        CoreAuthoringMap.allocate(seeds, converted.entryPointIndex().orElseThrow());
    List<CoreBinding> bindings = new ArrayList<>();
    for (int declaration = 0; declaration < converted.declarations().size(); declaration++) {
      BoundCoreConverter.Declaration value = converted.declarations().get(declaration);
      DefinitionOccurrenceId occurrence = allocation.occurrenceIds().get(declaration);
      value
          .bind(
              occurrence,
              exportedSources,
              pending -> {
                DefinitionId definition = canonical.definitionIds().get(pending.declarationIndex());
                if (definition == null) {
                  throw new IllegalStateException("namespace type reference is unresolved");
                }
                return new dev.w0fv1.norm.core.DefinitionReference.External(definition);
              })
          .ifPresent(bindings::add);
    }
    return new CoreCompilation(
        coreProgram,
        CoreNamespace.create(bindings),
        allocation.authoring(),
        new CoreBuildReport(
            converted.declarations().size(),
            canonical.groups().size(),
            counts.stored(),
            counts.reused(),
            counts.notAdmitted()),
        CoreDependencyIndex.create(coreProgram),
        CoreCompilationDelta.initial(coreProgram));
  }

  private StoreCounts store(List<dev.w0fv1.norm.core.CoreDefinitionGroup> groups) {
    int stored = 0;
    int reused = 0;
    int notAdmitted = 0;
    try {
      for (var group : groups) {
        PutResult result = store.put(group.canonicalBytes());
        if (!result.id().equals(group.id())) {
          throw new IllegalStateException("definition store returned a different content id");
        }
        switch (result.status()) {
          case STORED -> stored++;
          case REUSED -> reused++;
          case NOT_ADMITTED -> notAdmitted++;
        }
      }
      return new StoreCounts(stored, reused, notAdmitted);
    } catch (IOException exception) {
      throw new CompilationInfrastructureException("failed to persist core definitions", exception);
    }
  }

  private record StoreCounts(int stored, int reused, int notAdmitted) {}
}
