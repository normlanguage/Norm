package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.core.CompilationOutput;
import dev.w0fv1.norm.core.CompilationState;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreAuthoringMap;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreBuildReport;
import dev.w0fv1.norm.core.CoreCanonicalizationBudgetExceededException;
import dev.w0fv1.norm.core.CoreCanonicalizationCancelledException;
import dev.w0fv1.norm.core.CoreCanonicalizationControl;
import dev.w0fv1.norm.core.CoreCanonicalizer;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.core.CoreCompilationInput;
import dev.w0fv1.norm.core.CoreDependencyIndex;
import dev.w0fv1.norm.core.CoreMetadata;
import dev.w0fv1.norm.core.CoreNamespace;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.core.IncrementalAnalysisReport;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CoreBuilder {
  private final BoundProgram program;
  private final Set<DocumentId> exportedSources;
  private final Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates;
  private final CompilationGuard guard;

  CoreBuilder(
      BoundProgram program,
      Set<DocumentId> exportedSources,
      Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates,
      CompilationGuard guard) {
    this.program = Objects.requireNonNull(program, "program");
    this.exportedSources = Set.copyOf(exportedSources);
    this.sourceCoordinates = Map.copyOf(sourceCoordinates);
    this.guard = Objects.requireNonNull(guard, "guard");
  }

  Result build(
      CoreArtifact previous,
      CoreBuildHistory history,
      IncrementalAnalysisPlan analysis,
      ImportedCompilation imported) {
    guard.checkpoint();
    BoundCoreConverter.Result converted =
        new BoundCoreConverter(program, sourceCoordinates)
            .convert(previous, history, analysis, imported);
    CoreCanonicalizer.Result canonical;
    try {
      canonical =
          new CoreCanonicalizer()
              .canonicalize(
                  new CoreCompilationInput(
                      converted.declarations().stream()
                          .map(BoundCoreConverter.Declaration::input)
                          .toList(),
                      previous == null ? new CoreProgram(List.of()) : previous.program()),
                  new CoreCanonicalizationControl(
                      guard::isCancellationRequested, guard.maximumCanonicalSearchBranches()));
    } catch (CoreCanonicalizationCancelledException exception) {
      throw new CompilationCancelledException();
    } catch (CoreCanonicalizationBudgetExceededException exception) {
      throw new CompilationBudgetExceededException(
          "canonical search branch", guard.maximumCanonicalSearchBranches());
    }
    CoreProgram coreProgram = new CoreProgram(canonical.groups());
    List<CoreAuthoringMap.Seed> seeds = new ArrayList<>();
    for (int declaration = 0; declaration < converted.declarations().size(); declaration++) {
      guard.checkpoint();
      BoundCoreConverter.Declaration value = converted.declarations().get(declaration);
      seeds.add(
          new CoreAuthoringMap.Seed(
              canonical.definitionIds().get(declaration),
              canonical.definitionOrbits().get(declaration),
              value.role(),
              value.origin(),
              value.referenceTargets()));
    }
    CoreAuthoringMap.Allocation allocation =
        CoreAuthoringMap.allocate(
            seeds,
            converted.entryPointIndex().isPresent()
                ? java.util.OptionalInt.of(converted.entryPointIndex().orElseThrow())
                : java.util.OptionalInt.empty());
    List<CoreBinding> bindings = new ArrayList<>();
    for (int declaration = 0; declaration < converted.declarations().size(); declaration++) {
      guard.checkpoint();
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
                return new DefinitionReference.External(definition);
              },
              allocation.occurrenceIds()::get)
          .ifPresent(bindings::add);
    }
    CoreArtifact artifact =
        new CoreArtifact(
            coreProgram,
            CoreNamespace.create(bindings),
            allocation.authoring(),
            new CoreMetadata(
                converted.annotations().stream()
                    .map(
                        annotation ->
                            annotation.resolve(
                                canonical.definitionIds(), allocation.occurrenceIds()))
                    .toList()));
    var output =
        new CompilationOutput(
            artifact,
            new CompilationState(
                new CoreBuildReport(
                    converted.declarations().size(),
                    converted.convertedDefinitions(),
                    converted.relinkedDefinitions(),
                    converted.importedDefinitions(),
                    canonical.groups().size(),
                    canonical.metrics()),
                CoreDependencyIndex.create(coreProgram),
                CoreCompilationDelta.initial(coreProgram),
                IncrementalAnalysisReport.analyzed(converted.declarations().size(), 0)));
    Map<CoreBuildHistory.Key, DefinitionOccurrenceId> occurrences = new java.util.LinkedHashMap<>();
    converted
        .declarationIndices()
        .forEach((key, index) -> occurrences.put(key, allocation.occurrenceIds().get(index)));
    return new Result(
        output, new CoreBuildHistory(occurrences, converted.callableLocals(), converted.units()));
  }

  record Result(CompilationOutput output, CoreBuildHistory history) {}
}
