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
import dev.w0fv1.norm.value.CompilationScope;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class CompilerSession implements AutoCloseable {
  private final LanguageProfile profile;
  private final DefinitionStore definitionStore;
  private final CompilerSessionCapacity capacity;
  private final Runnable parseObserver;
  private final Runnable analysisObserver;
  private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);
  private final ReentrantLock stateLock = new ReentrantLock();
  private final GatePool<CompilationUnitId> unitLocks = new GatePool<>();
  private final GatePool<DocumentId> parseLocks = new GatePool<>();
  private final LinkedHashMap<ParseKey, ParsedDocument> parsedDocuments =
      new LinkedHashMap<>(16, 0.75f, true);
  private final LinkedHashMap<CompilationUnitId, TrackedUnit> compilations =
      new LinkedHashMap<>(16, 0.75f, true);
  private boolean closed;

  public CompilerSession() {
    this(LanguageProfile.kernel());
  }

  public CompilerSession(LanguageProfile profile) {
    this(profile, new InMemoryDefinitionStore(), CompilerSessionCapacity.standard());
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
    LanguageProfile profile = LanguageProfile.kernel();
    return persistent(profile);
  }

  public static CompilerSession persistent(LanguageProfile profile) throws IOException {
    Path root =
        Path.of(
            System.getProperty("user.home"),
            ".norm",
            "cache",
            "definitions",
            profile.identityVersion().storageNamespace());
    return persistent(root, profile);
  }

  public static CompilerSession persistent(Path root) throws IOException {
    return new CompilerSession(
        LanguageProfile.kernel(),
        new FileDefinitionStore(root),
        CompilerSessionCapacity.standard());
  }

  public static CompilerSession persistent(Path root, LanguageProfile profile) throws IOException {
    return new CompilerSession(
        profile, new FileDefinitionStore(root), CompilerSessionCapacity.standard());
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

  public CompilationResult compile(CompilationRequest request, CompilationControl control) {
    java.util.Objects.requireNonNull(request, "request");
    return withUnitLock(request.unit(), () -> compileUnit(request, control));
  }

  private CompilationResult compileUnit(CompilationRequest request, CompilationControl control) {
    CompilationGuard guard = java.util.Objects.requireNonNull(control, "control").begin();
    guard.validate(request);
    TrackedUnit cached = tracked(request.unit());
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

  public AnalysisResult analyze(CompilationRequest request) {
    return analyze(request, CompilationControl.standard());
  }

  public AnalysisResult analyze(CompilationRequest request, CompilationControl control) {
    return snapshot(request, control).analysis();
  }

  public AnalysisResult analyze(SourceFile source) {
    return analyze(source, CompilationControl.standard());
  }

  public AnalysisResult analyze(SourceFile source, CompilationControl control) {
    return snapshot(source, control).analysis();
  }

  public CompilationSnapshot snapshot(SourceFile source) {
    return snapshot(source, CompilationControl.standard());
  }

  public CompilationSnapshot snapshot(SourceFile source, CompilationControl control) {
    java.util.Objects.requireNonNull(source, "source");
    return snapshot(CompilationRequest.single(source), control);
  }

  public CompilationSnapshot snapshot(CompilationRequest request) {
    return snapshot(request, CompilationControl.standard());
  }

  public CompilationSnapshot snapshot(CompilationRequest request, CompilationControl control) {
    java.util.Objects.requireNonNull(request, "request");
    return withUnitLock(request.unit(), () -> snapshotUnit(request, control));
  }

  private CompilationSnapshot snapshotUnit(CompilationRequest request, CompilationControl control) {
    CompilationGuard guard = java.util.Objects.requireNonNull(control, "control").begin();
    guard.validate(request);
    TrackedUnit cached = tracked(request.unit());
    if (cached != null && cached.request().equals(request)) return cached.snapshot();
    PreparedCompilation prepared =
        prepare(request, false, false, guard, cached == null ? null : cached.snapshotFor(request));
    trackSnapshot(request, prepared.snapshot(), cached);
    return prepared.snapshot();
  }

  public void invalidate(DocumentId document) {
    java.util.Objects.requireNonNull(document, "document");
    withExclusiveLifecycle(
        () -> {
          stateLock.lock();
          try {
            parsedDocuments.keySet().removeIf(key -> key.document().equals(document));
            compilations
                .values()
                .removeIf(compilation -> compilation.documents().contains(document));
          } finally {
            stateLock.unlock();
          }
        });
  }

  public void invalidate(CompilationUnitId unit) {
    java.util.Objects.requireNonNull(unit, "unit");
    withExclusiveLifecycle(
        () -> {
          stateLock.lock();
          try {
            compilations.remove(unit);
          } finally {
            stateLock.unlock();
          }
        });
  }

  public Optional<SourceFile> preludeSource(DocumentId document) {
    return profile.preludeSource(document);
  }

  public CompilationSnapshot preludeSnapshot(DocumentId document) {
    return snapshot(profile.prelude().request(document));
  }

  public CompilationSnapshot preludeSnapshot(SourceFile source) {
    return preludeSnapshot(
        List.of(java.util.Objects.requireNonNull(source, "source")), source.id());
  }

  public CompilationSnapshot preludeSnapshot(
      java.util.Collection<SourceFile> overlays, DocumentId entryDocument) {
    Map<DocumentId, SourceFile> replacements = new LinkedHashMap<>();
    for (SourceFile source : List.copyOf(overlays)) {
      if (profile.preludeSource(source.id()).isEmpty()) {
        throw new IllegalArgumentException("source is not part of the compilation prelude");
      }
      if (replacements.putIfAbsent(source.id(), source) != null) {
        throw new IllegalArgumentException("duplicate prelude source overlay");
      }
    }
    CompilationRequest request = profile.prelude().request(entryDocument);
    List<SourceFile> sources =
        request.sources().stream()
            .map(candidate -> replacements.getOrDefault(candidate.id(), candidate))
            .toList();
    return snapshot(
        new CompilationRequest(
            request.unit(),
            request.scope(),
            request.entryDocument(),
            sources,
            request.exportedSources()));
  }

  @Override
  public void close() {
    lifecycleLock.writeLock().lock();
    try {
      if (closed) return;
      stateLock.lock();
      try {
        parsedDocuments.clear();
        compilations.clear();
        closed = true;
      } finally {
        stateLock.unlock();
      }
    } finally {
      lifecycleLock.writeLock().unlock();
    }
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
        .prelude()
        .documents()
        .forEach(parsed -> parsedByDocument.put(parsed.source().id(), parsed));
    for (SourceFile source : request.sources()) {
      guard.checkpoint();
      parsedByDocument.put(source.id(), parse(source, guard));
    }
    List<ParsedDocument> parsed = List.copyOf(parsedByDocument.values());
    parsed.forEach(document -> document.diagnostics().forEach(diagnostics::report));
    List<Syntax.Program> programs =
        parsed.stream()
            .map(ParsedDocument::syntax)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Syntax.Program entryProgram = null;
    Set<DocumentId> exportedSources = new LinkedHashSet<>(profile.prelude().exportedSources());
    exportedSources.addAll(request.exportedSources());
    CompilationScope sourceScope = request.scope();
    if (profile.prelude().scope().isPresent()) {
      CompilationScope preludeScope = profile.prelude().scope().orElseThrow();
      sourceScope = preludeScope.merge(sourceScope);
      Set<dev.w0fv1.norm.value.ModuleCoordinate> preludeExports =
          profile.prelude().exportedSources().stream()
              .map(preludeScope::coordinate)
              .map(ModuleSourceCoordinate::module)
              .collect(java.util.stream.Collectors.toSet());
      sourceScope = sourceScope.withReads(request.scope().modules().modules(), preludeExports);
    }
    for (ParsedDocument document : parsed) {
      if (document.source().id().equals(request.entryDocument())) entryProgram = document.syntax();
    }
    IncrementalAnalysisPlan analysisPlan = IncrementalAnalysisPlan.create(previous, parsed);
    analysisObserver.run();
    Syntax.Program resolvedEntryProgram = java.util.Objects.requireNonNull(entryProgram);
    DeclarationCatalog declarations =
        new DeclarationCatalog(programs, exportedSources, sourceScope);
    SemanticAnalysisInput analysisInput =
        new SemanticAnalysisInput(
            programs,
            resolvedEntryProgram,
            requireEntryPoint,
            exportedSources,
            analysisPlan.reusable(),
            previous == null ? 0 : previous.semanticModel().nextSourceSymbolOrdinal(),
            profile.moduleEvaluationDocuments(),
            profile.standardLibraryDocuments(),
            request.bindingSources(),
            sourceScope,
            declarations);
    FrontendAnalysis analyzed =
        new Analyzer(analysisInput, diagnostics, guard).analyze(resolveProgram);
    CompilationSnapshot snapshot =
        new CompilationSnapshot(request.entryDocument(), parsed, analyzed.analysis());
    return new PreparedCompilation(
        snapshot,
        analyzed.resolvedProgram(),
        exportedSources,
        sourceScope.coordinates(),
        analysisPlan);
  }

  private ParsedDocument parse(SourceFile source, CompilationGuard guard) {
    ParseKey key = new ParseKey(source.id());
    return parseLocks.withLock(
        source.id(),
        () -> {
          stateLock.lock();
          try {
            ParsedDocument existing = parsedDocuments.get(key);
            if (existing != null && existing.source().text().equals(source.text())) return existing;
          } finally {
            stateLock.unlock();
          }
          parseObserver.run();
          ParsedDocument parsed = SourceParser.parse(source, guard);
          stateLock.lock();
          try {
            parsedDocuments.put(key, parsed);
            evictParsedDocuments();
          } finally {
            stateLock.unlock();
          }
          return parsed;
        });
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
    stateLock.lock();
    try {
      compilations.put(
          request.unit(), new TrackedUnit(request, result, tracked, documents, snapshot));
      evictCompilations();
    } finally {
      stateLock.unlock();
    }
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
    stateLock.lock();
    try {
      compilations.put(
          request.unit(),
          new TrackedUnit(
              request,
              result,
              previous == null ? null : previous.lastSuccessfulOutput(),
              documents,
              snapshot));
      evictCompilations();
    } finally {
      stateLock.unlock();
    }
  }

  private void trackSnapshot(
      CompilationRequest request, CompilationSnapshot snapshot, TrackedUnit previous) {
    Set<DocumentId> documents =
        request.sources().stream()
            .map(SourceFile::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    stateLock.lock();
    try {
      compilations.put(
          request.unit(),
          new TrackedUnit(
              request,
              null,
              previous == null ? null : previous.lastSuccessfulOutput(),
              documents,
              snapshot));
      evictCompilations();
    } finally {
      stateLock.unlock();
    }
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

  private TrackedUnit tracked(CompilationUnitId unit) {
    stateLock.lock();
    try {
      return compilations.get(unit);
    } finally {
      stateLock.unlock();
    }
  }

  private <T> T withUnitLock(CompilationUnitId unit, Supplier<T> operation) {
    lifecycleLock.readLock().lock();
    try {
      requireOpen();
      return unitLocks.withLock(unit, operation);
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void withExclusiveLifecycle(Runnable operation) {
    lifecycleLock.writeLock().lock();
    try {
      requireOpen();
      operation.run();
    } finally {
      lifecycleLock.writeLock().unlock();
    }
  }

  private static final class GatePool<K> {
    private final ConcurrentHashMap<K, Gate> gates = new ConcurrentHashMap<>();

    private <T> T withLock(K key, Supplier<T> operation) {
      Gate gate =
          gates.compute(
              key,
              (ignored, current) -> {
                Gate acquired = current == null ? new Gate() : current;
                acquired.users++;
                return acquired;
              });
      gate.lock.lock();
      try {
        return operation.get();
      } finally {
        gate.lock.unlock();
        gates.computeIfPresent(
            key,
            (ignored, current) -> {
              if (current != gate) throw new IllegalStateException("gate identity changed");
              current.users--;
              return current.users == 0 ? null : current;
            });
      }
    }
  }

  private static final class Gate {
    private final ReentrantLock lock = new ReentrantLock();
    private int users;
  }

  private record ParseKey(DocumentId document) {}

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
