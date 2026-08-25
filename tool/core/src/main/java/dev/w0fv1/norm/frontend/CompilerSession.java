package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.core.CompilationOutput;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.core.IncrementalAnalysisReport;
import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.FileDefinitionStore;
import dev.w0fv1.norm.core.store.InMemoryDefinitionStore;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CompilerSession implements AutoCloseable {
  private final LanguageProfile profile;
  private final DefinitionStore definitionStore;
  private final CompilerSessionCapacity capacity;
  private final Runnable parseObserver;
  private final Runnable analysisObserver;
  private final LinkedHashMap<ParseKey, ParsedDocument> parsedDocuments =
      new LinkedHashMap<>(16, 0.75f, true);
  private final LinkedHashMap<CompilationUnitId, TrackedUnit> compilations =
      new LinkedHashMap<>(16, 0.75f, true);
  private boolean closed;

  public CompilerSession() {
    this(
        LanguageProfile.current(),
        new InMemoryDefinitionStore(),
        CompilerSessionCapacity.standard());
  }

  public CompilerSession(
      LanguageProfile profile, DefinitionStore definitionStore, CompilerSessionCapacity capacity) {
    this(profile, definitionStore, capacity, () -> {}, () -> {});
  }

  CompilerSession(
      LanguageProfile profile,
      DefinitionStore definitionStore,
      CompilerSessionCapacity capacity,
      Runnable parseObserver,
      Runnable analysisObserver) {
    this.profile = java.util.Objects.requireNonNull(profile, "profile");
    this.definitionStore = java.util.Objects.requireNonNull(definitionStore, "definitionStore");
    this.capacity = java.util.Objects.requireNonNull(capacity, "capacity");
    this.parseObserver = java.util.Objects.requireNonNull(parseObserver, "parseObserver");
    this.analysisObserver = java.util.Objects.requireNonNull(analysisObserver, "analysisObserver");
  }

  public static CompilerSession persistent() throws IOException {
    LanguageProfile profile = LanguageProfile.current();
    Path root =
        Path.of(
            System.getProperty("user.home"),
            ".norm",
            "cache",
            "definitions",
            profile.identityVersion().storageNamespace());
    return persistent(root);
  }

  public static CompilerSession persistent(Path root) throws IOException {
    return new CompilerSession(
        LanguageProfile.current(),
        new FileDefinitionStore(root),
        CompilerSessionCapacity.standard());
  }

  public CompilationResult compile(SourceFile source) {
    return compile(source, CompilationControl.standard());
  }

  public CompilationResult compile(SourceFile source, CompilationControl control) {
    return compile(CompilationRequest.single(source), control);
  }

  public CompilationResult compile(CompilationRequest request) {
    return compile(request, CompilationControl.standard());
  }

  public synchronized CompilationResult compile(
      CompilationRequest request, CompilationControl control) {
    requireOpen();
    java.util.Objects.requireNonNull(request, "request");
    CompilationGuard guard = java.util.Objects.requireNonNull(control, "control").begin();
    guard.validate(request);
    TrackedUnit cached = compilations.get(request.unit());
    if (cached != null && cached.request().equals(request) && cached.cachedResult() != null) {
      return cached.reuse();
    }
    long analysisStarted = System.nanoTime();
    PreparedCompilation prepared =
        prepare(request, true, true, guard, cached == null ? null : cached.snapshotFor(request));
    long analysisElapsed = Math.max(0, System.nanoTime() - analysisStarted);
    AnalysisResult analysis = prepared.snapshot().analysis();
    if (analysis.hasErrors() || prepared.resolvedProgram().isEmpty()) {
      CompilationResult failed = new CompilationResult(Optional.empty(), analysis.diagnostics());
      trackAnalysis(request, failed, prepared.snapshot(), cached);
      return failed;
    }
    CompilationOutput built =
        new CoreBuilder(
                prepared.resolvedProgram().orElseThrow(),
                prepared.exportedSources(),
                prepared.sourceCoordinates(),
                definitionStore,
                guard)
            .build();
    CompilationOutput measured =
        built.withAnalysisReport(
            new IncrementalAnalysisReport(
                prepared.analysisPlan().declarations(),
                prepared.analysisPlan().analyzedDeclarations(),
                prepared.analysisPlan().reusedDeclarations(),
                analysisElapsed));
    CompilationOutput output =
        trackCompilation(request, measured, analysis, prepared.snapshot(), cached);
    return new CompilationResult(Optional.of(new TypedProgram(output)), analysis.diagnostics());
  }

  public synchronized AnalysisResult analyze(CompilationRequest request) {
    return analyze(request, CompilationControl.standard());
  }

  public synchronized AnalysisResult analyze(
      CompilationRequest request, CompilationControl control) {
    return snapshot(request, control).analysis();
  }

  public synchronized AnalysisResult analyze(SourceFile source) {
    return analyze(source, CompilationControl.standard());
  }

  public synchronized AnalysisResult analyze(SourceFile source, CompilationControl control) {
    return snapshot(source, control).analysis();
  }

  public synchronized CompilationSnapshot snapshot(SourceFile source) {
    return snapshot(source, CompilationControl.standard());
  }

  public synchronized CompilationSnapshot snapshot(SourceFile source, CompilationControl control) {
    requireOpen();
    java.util.Objects.requireNonNull(source, "source");
    return snapshot(CompilationRequest.single(source), control);
  }

  public synchronized CompilationSnapshot snapshot(CompilationRequest request) {
    return snapshot(request, CompilationControl.standard());
  }

  public synchronized CompilationSnapshot snapshot(
      CompilationRequest request, CompilationControl control) {
    requireOpen();
    CompilationGuard guard = java.util.Objects.requireNonNull(control, "control").begin();
    guard.validate(request);
    TrackedUnit cached = compilations.get(request.unit());
    PreparedCompilation prepared =
        prepare(request, false, false, guard, cached == null ? null : cached.snapshotFor(request));
    trackSnapshot(request, prepared.snapshot(), cached);
    return prepared.snapshot();
  }

  public synchronized void invalidate(DocumentId document) {
    requireOpen();
    java.util.Objects.requireNonNull(document, "document");
    parsedDocuments.keySet().removeIf(key -> key.document().equals(document));
    compilations.values().removeIf(compilation -> compilation.documents().contains(document));
  }

  public synchronized void invalidate(CompilationUnitId unit) {
    requireOpen();
    compilations.remove(java.util.Objects.requireNonNull(unit, "unit"));
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    parsedDocuments.clear();
    compilations.clear();
    closed = true;
  }

  private PreparedCompilation prepare(
      CompilationRequest request,
      boolean requireEntryPoint,
      boolean resolveProgram,
      CompilationGuard guard,
      CompilationSnapshot previous) {
    java.util.Objects.requireNonNull(request, "request");
    DiagnosticBag diagnostics = new DiagnosticBag();
    LinkedHashMap<DocumentId, ParsedDocument> parsedByDocument = new LinkedHashMap<>();
    profile
        .standardLibrary()
        .documents()
        .forEach(parsed -> parsedByDocument.put(parsed.source().id(), parsed));
    for (SourceFile source : request.sources()) {
      guard.checkpoint();
      parsedByDocument.put(source.id(), parse(source, ProjectLoader.isManifest(source), guard));
    }
    List<ParsedDocument> parsed = List.copyOf(parsedByDocument.values());
    parsed.forEach(document -> document.diagnostics().forEach(diagnostics::report));
    List<Syntax.Program> programs =
        parsed.stream()
            .map(ParsedDocument::syntax)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Syntax.Program entryProgram = null;
    Set<DocumentId> exportedSources =
        new LinkedHashSet<>(profile.standardLibrary().exportedSources());
    exportedSources.addAll(request.exportedSources());
    Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates =
        new LinkedHashMap<>(profile.standardLibrary().scope().coordinates());
    sourceCoordinates.putAll(request.scope().coordinates());
    for (ParsedDocument document : parsed) {
      if (document.source().id().equals(request.entryDocument())) entryProgram = document.syntax();
    }
    IncrementalAnalysisPlan analysisPlan = IncrementalAnalysisPlan.create(previous, parsed);
    analysisObserver.run();
    FrontendAnalysis analyzed =
        new Analyzer(
                programs,
                java.util.Objects.requireNonNull(entryProgram),
                diagnostics,
                requireEntryPoint,
                exportedSources,
                guard,
                analysisPlan.reusable(),
                previous == null ? 0 : previous.semanticModel().nextSourceSymbolOrdinal())
            .analyze(resolveProgram);
    CompilationSnapshot snapshot =
        new CompilationSnapshot(request.entryDocument(), parsed, analyzed.analysis());
    return new PreparedCompilation(
        snapshot, analyzed.resolvedProgram(), exportedSources, sourceCoordinates, analysisPlan);
  }

  private ParsedDocument parse(SourceFile source, boolean manifest, CompilationGuard guard) {
    ParseKey key = new ParseKey(source.id(), manifest);
    ParsedDocument existing = parsedDocuments.get(key);
    if (existing != null && existing.source().text().equals(source.text())) return existing;
    parseObserver.run();
    ParsedDocument parsed = SourceParser.parse(source, manifest, guard);
    parsedDocuments.put(key, parsed);
    evictParsedDocuments();
    return parsed;
  }

  private CompilationOutput trackCompilation(
      CompilationRequest request,
      CompilationOutput output,
      AnalysisResult analysis,
      CompilationSnapshot snapshot,
      TrackedUnit previous) {
    CompilationOutput tracked =
        previous == null || previous.lastSuccessfulOutput() == null
            ? output
            : output.withDelta(
                CoreCompilationDelta.between(
                    previous.lastSuccessfulOutput().artifact().program(),
                    output.artifact().program()));
    CompilationResult result =
        new CompilationResult(Optional.of(new TypedProgram(tracked)), analysis.diagnostics());
    Set<DocumentId> documents =
        request.sources().stream()
            .map(SourceFile::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    compilations.put(
        request.unit(), new TrackedUnit(request, result, tracked, documents, snapshot));
    evictCompilations();
    return tracked;
  }

  private void trackAnalysis(
      CompilationRequest request,
      CompilationResult result,
      CompilationSnapshot snapshot,
      TrackedUnit previous) {
    Set<DocumentId> documents =
        request.sources().stream()
            .map(SourceFile::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    compilations.put(
        request.unit(),
        new TrackedUnit(
            request,
            result,
            previous == null ? null : previous.lastSuccessfulOutput(),
            documents,
            snapshot));
    evictCompilations();
  }

  private void trackSnapshot(
      CompilationRequest request, CompilationSnapshot snapshot, TrackedUnit previous) {
    Set<DocumentId> documents =
        request.sources().stream()
            .map(SourceFile::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    compilations.put(
        request.unit(),
        new TrackedUnit(
            request,
            null,
            previous == null ? null : previous.lastSuccessfulOutput(),
            documents,
            snapshot));
    evictCompilations();
  }

  private void evictParsedDocuments() {
    Iterator<ParseKey> entries = parsedDocuments.keySet().iterator();
    while (parsedDocuments.size() > capacity.parsedDocuments()) {
      entries.next();
      entries.remove();
    }
  }

  private void evictCompilations() {
    Iterator<CompilationUnitId> entries = compilations.keySet().iterator();
    while (compilations.size() > capacity.compilationUnits()) {
      entries.next();
      entries.remove();
    }
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("compiler session is closed");
  }

  private record ParseKey(DocumentId document, boolean manifest) {}

  private record TrackedUnit(
      CompilationRequest request,
      CompilationResult cachedResult,
      CompilationOutput lastSuccessfulOutput,
      Set<DocumentId> documents,
      CompilationSnapshot snapshot) {
    private TrackedUnit {
      java.util.Objects.requireNonNull(request, "request");
      documents = Set.copyOf(documents);
      java.util.Objects.requireNonNull(snapshot, "snapshot");
    }

    CompilationResult reuse() {
      if (cachedResult.program().isEmpty()) return cachedResult;
      CoreArtifact artifact = lastSuccessfulOutput.artifact();
      CompilationOutput reused =
          lastSuccessfulOutput
              .withDelta(CoreCompilationDelta.between(artifact.program(), artifact.program()))
              .withAnalysisReport(
                  IncrementalAnalysisReport.reused(
                      lastSuccessfulOutput.state().analysisReport().declarations()));
      return new CompilationResult(
          Optional.of(new TypedProgram(reused)), cachedResult.diagnostics());
    }

    CompilationSnapshot snapshotFor(CompilationRequest current) {
      return request.entryDocument().equals(current.entryDocument())
              && request.scope().equals(current.scope())
              && request.exportedSources().equals(current.exportedSources())
          ? snapshot
          : null;
    }
  }

  private record PreparedCompilation(
      CompilationSnapshot snapshot,
      Optional<BoundProgram> resolvedProgram,
      Set<DocumentId> exportedSources,
      Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates,
      IncrementalAnalysisPlan analysisPlan) {
    private PreparedCompilation {
      java.util.Objects.requireNonNull(snapshot, "snapshot");
      resolvedProgram = java.util.Objects.requireNonNull(resolvedProgram, "resolvedProgram");
      exportedSources = Set.copyOf(exportedSources);
      sourceCoordinates = Map.copyOf(sourceCoordinates);
      java.util.Objects.requireNonNull(analysisPlan, "analysisPlan");
    }
  }
}
