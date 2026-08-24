package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.core.CoreCompilation;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Compiler {
  private final CompilationEnvironment environment;
  private final Map<CompilationUnitId, CoreCompilation> compilations = new HashMap<>();

  public Compiler() {
    this(CompilationEnvironment.standard());
  }

  public Compiler(CompilationEnvironment environment) {
    this.environment = java.util.Objects.requireNonNull(environment, "environment");
  }

  public CompilationResult compile(SourceFile source) {
    return compile(CompilationRequest.single(source));
  }

  public synchronized CompilationResult compile(CompilationRequest request) {
    java.util.Objects.requireNonNull(request, "request");
    PreparedCompilation prepared = prepare(request, true, true);
    AnalysisResult analysis = prepared.snapshot().analysis();
    if (analysis.hasErrors() || prepared.resolvedProgram().isEmpty()) {
      return new CompilationResult(Optional.empty(), analysis.diagnostics());
    }
    CoreCompilation core =
        track(
            request,
            new CoreBuilder(
                    prepared.resolvedProgram().orElseThrow(),
                    prepared.exportedSources(),
                    prepared.sourceCoordinates(),
                    environment.definitionStore())
                .build());
    return new CompilationResult(Optional.of(new TypedProgram(core)), analysis.diagnostics());
  }

  public AnalysisResult analyze(CompilationRequest request) {
    return snapshot(request).analysis();
  }

  public CompilationSnapshot snapshot(SourceFile source) {
    java.util.Objects.requireNonNull(source, "source");
    return prepare(CompilationRequest.single(source), false, false).snapshot();
  }

  public CompilationSnapshot snapshot(CompilationRequest request) {
    return prepare(request, false, false).snapshot();
  }

  private PreparedCompilation prepare(
      CompilationRequest request, boolean requireEntryPoint, boolean resolveProgram) {
    java.util.Objects.requireNonNull(request, "request");
    DiagnosticBag diagnostics = new DiagnosticBag();
    java.util.LinkedHashMap<dev.w0fv1.norm.value.DocumentId, ParsedDocument> parsedByDocument =
        new java.util.LinkedHashMap<>();
    environment
        .standardLibrary()
        .documents()
        .forEach(parsed -> parsedByDocument.put(parsed.source().id(), parsed));
    for (SourceFile source : request.sources()) {
      parsedByDocument.put(
          source.id(), environment.parse(source, ProjectLoader.isManifest(source)));
    }
    List<ParsedDocument> parsedDocuments = List.copyOf(parsedByDocument.values());
    parsedDocuments.forEach(parsed -> parsed.diagnostics().forEach(diagnostics::report));
    List<Syntax.Program> programs =
        parsedDocuments.stream()
            .map(ParsedDocument::syntax)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Syntax.Program entryProgram = null;
    Set<dev.w0fv1.norm.value.DocumentId> exportedSources =
        new LinkedHashSet<>(environment.standardLibrary().exportedSources());
    exportedSources.addAll(request.exportedSources());
    Map<dev.w0fv1.norm.value.DocumentId, ModuleSourceCoordinate> sourceCoordinates =
        new java.util.LinkedHashMap<>(environment.standardLibrary().scope().coordinates());
    sourceCoordinates.putAll(request.scope().coordinates());
    for (ParsedDocument parsed : parsedDocuments) {
      if (parsed.source().id().equals(request.entryDocument())) entryProgram = parsed.syntax();
    }
    environment.analysisStarted();
    FrontendAnalysis analyzed =
        new Analyzer(
                programs,
                java.util.Objects.requireNonNull(entryProgram),
                diagnostics,
                requireEntryPoint,
                exportedSources)
            .analyze(resolveProgram);
    CompilationSnapshot snapshot =
        new CompilationSnapshot(request.entryDocument(), parsedDocuments, analyzed.analysis());
    return new PreparedCompilation(
        snapshot, analyzed.resolvedProgram(), exportedSources, sourceCoordinates);
  }

  public AnalysisResult analyze(SourceFile source) {
    return snapshot(source).analysis();
  }

  private CoreCompilation track(CompilationRequest request, CoreCompilation compilation) {
    CoreCompilation previous = compilations.get(request.unit());
    CoreCompilation tracked =
        previous == null
            ? compilation
            : compilation.withDelta(
                CoreCompilationDelta.between(previous.program(), compilation.program()));
    compilations.put(request.unit(), tracked);
    return tracked;
  }

  private record PreparedCompilation(
      CompilationSnapshot snapshot,
      Optional<BoundProgram> resolvedProgram,
      Set<dev.w0fv1.norm.value.DocumentId> exportedSources,
      Map<dev.w0fv1.norm.value.DocumentId, ModuleSourceCoordinate> sourceCoordinates) {
    private PreparedCompilation {
      java.util.Objects.requireNonNull(snapshot, "snapshot");
      resolvedProgram = java.util.Objects.requireNonNull(resolvedProgram, "resolvedProgram");
      exportedSources = Set.copyOf(exportedSources);
      sourceCoordinates = Map.copyOf(sourceCoordinates);
    }
  }
}
