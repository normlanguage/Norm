package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.core.CompilationOutput;
import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.core.IncrementalAnalysisReport;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
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
  private CompilationResultCache resultCache;

  public CompilerSession() {
    this(LanguageProfile.kernel());
  }

  public CompilerSession(LanguageProfile profile) {
    this(profile, CompilerSessionCapacity.standard());
  }

  public CompilerSession(LanguageProfile profile, CompilerSessionCapacity capacity) {
    this(profile, capacity, () -> {}, () -> {});
  }

  CompilerSession(
      LanguageProfile profile,
      CompilerSessionCapacity capacity,
      Runnable parseObserver,
      Runnable analysisObserver) {
    this.profile = java.util.Objects.requireNonNull(profile, "profile");
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
            "compiler",
            profile.identityVersion().storageNamespace());
    return persistent(root, profile);
  }

  public static CompilerSession persistent(Path root) throws IOException {
    return persistent(root, LanguageProfile.kernel());
  }

  public static CompilerSession persistent(Path root, LanguageProfile profile) throws IOException {
    var session = new CompilerSession(profile, CompilerSessionCapacity.standard());
    session.resultCache = new CompilationResultCache(root.resolve("compilations"), profile);
    return session;
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
    return withUnitLock(
        request.unit(), () -> compileUnit(request, control, List.of(), false).compilation());
  }

  public CompilationResult compile(CompilationRequest request, List<CompiledModule> modules) {
    var imported = List.copyOf(modules);
    return withUnitLock(
        request.unit(),
        () -> compileUnit(request, CompilationControl.standard(), imported, false).compilation());
  }

  public ModuleCompilation compileModule(CompilationRequest request, ModuleCoordinate module) {
    return compileModule(request, module, List.of());
  }

  public ModuleCompilation compileModule(
      CompilationRequest request, ModuleCoordinate module, List<CompiledModule> modules) {
    var imported = List.copyOf(modules);
    if (request.kind() != CompilationRequest.Kind.LIBRARY)
      throw new IllegalArgumentException("module compilation requires a library request");
    return withUnitLock(
        request.unit(),
        () -> {
          var product = compileUnit(request, CompilationControl.standard(), imported, true);
          var compiled =
              product.compilation().isSuccess()
                  ? Optional.of(
                      CompiledModule.capture(
                          module,
                          product.snapshot(),
                          product.history(),
                          product.compilation().output().orElseThrow().artifact(),
                          request.exportedSources(),
                          request.bindingSources()))
                  : Optional.<CompiledModule>empty();
          return new ModuleCompilation(product.compilation(), compiled);
        });
  }

  private CompilationProduct compileUnit(
      CompilationRequest request,
      CompilationControl control,
      List<CompiledModule> modules,
      boolean requireHistory) {
    CompilationGuard guard = java.util.Objects.requireNonNull(control, "control").begin();
    guard.validate(request);
    var moduleIds =
        modules.stream()
            .sorted(java.util.Comparator.comparing(CompiledModule::coordinate))
            .map(CompiledModule::contentId)
            .toList();
    TrackedUnit cached = tracked(request.unit());
    if (cached != null && !cached.compiledModules().equals(moduleIds)) cached = null;
    if (cached != null && cached.request().equals(request) && cached.cachedResult() != null) {
      return new CompilationProduct(cached.reuse(), cached.snapshot(), cached.coreHistory());
    }
    dev.w0fv1.norm.value.Sha256Digest resultKey =
        resultCache == null ? null : resultCache.key(request, moduleIds);
    if (resultKey != null && cached == null && !requireHistory) {
      try {
        var stored = resultCache.read(resultKey);
        if (stored.isPresent()) return new CompilationProduct(stored.orElseThrow(), null, null);
      } catch (IOException exception) {
        throw new CompilationInfrastructureException("cannot read compilation result", exception);
      }
    }
    IncrementalAnalysisPlan.History previousAnalysis =
        cached == null ? null : cached.historyFor(request);
    CoreBuildHistory previousCore =
        cached == null || previousAnalysis == null ? null : cached.coreHistory();
    CompilationOutput previousOutput = cached == null ? null : cached.lastSuccessfulOutput();
    if (cached == null && resultCache != null) {
      try {
        var history = resultCache.readHistory(request, moduleIds).orElse(null);
        if (history != null) {
          previousAnalysis = history.analysis();
          previousCore = history.core();
          previousOutput =
              resultCache.read(history.resultKey()).flatMap(CompilationResult::output).orElse(null);
        }
      } catch (IOException exception) {
        throw new CompilationInfrastructureException("cannot read compilation history", exception);
      }
    }
    long analysisStarted = System.nanoTime();
    PreparedCompilation prepared =
        prepare(
            request,
            request.kind() == CompilationRequest.Kind.APPLICATION,
            true,
            guard,
            previousAnalysis,
            modules,
            previousOutput != null && previousCore != null);
    long analysisElapsed = Math.max(0, System.nanoTime() - analysisStarted);
    AnalysisResult analysis = prepared.snapshot().analysis();
    if (analysis.hasErrors() || prepared.resolvedProgram().isEmpty()) {
      CompilationResult failed = new CompilationResult(Optional.empty(), analysis.diagnostics());
      trackAnalysis(request, failed, prepared.snapshot(), cached, moduleIds);
      return new CompilationProduct(failed, prepared.snapshot(), null);
    }
    CoreBuilder.Result built =
        new CoreBuilder(
                prepared.resolvedProgram().orElseThrow(),
                prepared.exportedSources(),
                prepared.sourceCoordinates(),
                guard)
            .build(
                previousOutput == null ? null : previousOutput.artifact(),
                previousCore,
                prepared.analysisPlan(),
                prepared.imported());
    CompilationOutput measured =
        built
            .output()
            .withAnalysisReport(
                new IncrementalAnalysisReport(
                    prepared.analysisPlan().declarations(),
                    prepared.analysisPlan().analyzedDeclarations(),
                    prepared.analysisPlan().reusedDeclarations(),
                    analysisElapsed));
    CompilationOutput output =
        trackCompilation(
            request,
            measured,
            analysis,
            prepared.snapshot(),
            built.history(),
            previousOutput,
            moduleIds);
    var result = new CompilationResult(Optional.of(output), analysis.diagnostics());
    if (resultKey != null) {
      try {
        resultCache.write(resultKey, result);
        resultCache.writeHistory(
            request,
            moduleIds,
            new CompilationHistory(prepared.snapshot().history(), built.history(), resultKey));
      } catch (IOException exception) {
        throw new CompilationInfrastructureException("cannot store compilation result", exception);
      }
    }
    return new CompilationProduct(result, prepared.snapshot(), built.history());
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
        prepare(
            request,
            false,
            false,
            guard,
            cached == null ? null : cached.historyFor(request),
            List.of(),
            false);
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

  public CompilationSnapshot preludeSnapshot(SourceFile source) {
    return preludeSnapshot(
        List.of(java.util.Objects.requireNonNull(source, "source")), source.id());
  }

  public CompilationSnapshot preludeSnapshot(
      java.util.Collection<SourceFile> overlays, DocumentId entryDocument) {
    return preludeSnapshot(overlays, entryDocument, CompilationControl.standard());
  }

  public CompilationSnapshot preludeSnapshot(
      java.util.Collection<SourceFile> overlays,
      DocumentId entryDocument,
      CompilationControl control) {
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
            request.exportedSources()),
        control);
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
      IncrementalAnalysisPlan.History previous,
      List<CompiledModule> modules,
      boolean reusableCore) {
    java.util.Objects.requireNonNull(request, "request");
    DiagnosticBag diagnostics = new DiagnosticBag();
    CompilationPrelude prelude =
        profile.moduleEvaluationDocuments().isEmpty()
            ? profile.prelude().excludingModules(request.scope().modules().modules())
            : profile.prelude();
    Set<DocumentId> standardDocuments = new LinkedHashSet<>(profile.standardLibraryDocuments());
    request
        .scope()
        .coordinates()
        .forEach(
            (id, coordinate) -> {
              if (coordinate.module().name().equals("std")) standardDocuments.add(id);
            });
    LinkedHashMap<DocumentId, ParsedDocument> parsedByDocument = new LinkedHashMap<>();
    prelude.documents().forEach(parsed -> parsedByDocument.put(parsed.source().id(), parsed));
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
    Set<DocumentId> exportedSources = new LinkedHashSet<>(prelude.exportedSources());
    exportedSources.addAll(request.exportedSources());
    CompilationScope sourceScope = request.scope();
    if (prelude.scope().isPresent()) {
      CompilationScope preludeScope = prelude.scope().orElseThrow();
      sourceScope = preludeScope.merge(sourceScope);
      Set<ModuleCoordinate> preludeExports =
          prelude.exportedSources().stream()
              .map(preludeScope::coordinate)
              .map(ModuleSourceCoordinate::module)
              .collect(java.util.stream.Collectors.toSet());
      sourceScope = sourceScope.withReads(request.scope().modules().modules(), preludeExports);
    }
    for (ParsedDocument document : parsed) {
      if (document.source().id().equals(request.entryDocument())) entryProgram = document.syntax();
    }
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
            Math.max(
                previous == null ? 0 : previous.nextSymbolOrdinal(),
                modules.stream().mapToInt(module -> module.nextSymbolOrdinal).max().orElse(0)),
            profile.moduleEvaluationDocuments(),
            standardDocuments,
            request.bindingSources(),
            sourceScope,
            declarations);
    Analyzer analyzer = new Analyzer(analysisInput, diagnostics, guard);
    IncrementalAnalysisPlan analysisPlan =
        IncrementalAnalysisPlan.create(previous, parsed, sourceScope, analyzer.declarations());
    var importedModules = modules;
    if (!modules.isEmpty()) {
      var sources = new LinkedHashMap<ModuleSourceCoordinate, SourceFile>();
      for (var document : parsed)
        sources.put(sourceScope.coordinate(document.source().id()), document.source());
      modules.forEach(module -> module.verifySources(sources));
      if (reusableCore) {
        var changedModules = analysisPlan.analyzedModules(parsed, sourceScope);
        importedModules =
            modules.stream()
                .filter(module -> changedModules.contains(module.coordinate()))
                .toList();
      }
    }
    ImportedCompilation imported =
        ImportedCompilation.create(
            importedModules,
            parsed,
            sourceScope,
            analyzer.declarations(),
            exportedSources,
            request.bindingSources());
    analysisPlan = imported.merge(analysisPlan);
    FrontendAnalysis analyzed =
        analyzer.analyze(resolveProgram, request.kind(), analysisPlan.reusable());
    CompilationSnapshot snapshot =
        new CompilationSnapshot(
            request.entryDocument(), parsed, analyzed.analysis(), analyzer.declarations());
    return new PreparedCompilation(
        snapshot,
        analyzed.resolvedProgram(),
        exportedSources,
        sourceScope.coordinates(),
        analysisPlan,
        imported);
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
      CoreBuildHistory coreHistory,
      CompilationOutput previous,
      List<dev.w0fv1.norm.value.Sha256Digest> moduleIds) {
    CompilationOutput tracked =
        previous == null
            ? output
            : output.withDelta(
                CoreCompilationDelta.between(
                    previous.artifact().program(), output.artifact().program()));
    CompilationResult result = new CompilationResult(Optional.of(tracked), analysis.diagnostics());
    Set<DocumentId> documents =
        request.sources().stream()
            .map(SourceFile::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    stateLock.lock();
    try {
      compilations.put(
          request.unit(),
          new TrackedUnit(request, result, tracked, documents, snapshot, coreHistory, moduleIds));
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
      TrackedUnit previous,
      List<dev.w0fv1.norm.value.Sha256Digest> moduleIds) {
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
              snapshot,
              null,
              moduleIds));
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
              snapshot,
              null,
              List.of()));
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
      CompilationSnapshot snapshot,
      CoreBuildHistory coreHistory,
      List<dev.w0fv1.norm.value.Sha256Digest> compiledModules) {
    private TrackedUnit {
      java.util.Objects.requireNonNull(request, "request");
      documents = Set.copyOf(documents);
      compiledModules = List.copyOf(compiledModules);
      java.util.Objects.requireNonNull(snapshot, "snapshot");
    }

    CompilationResult reuse() {
      if (cachedResult.output().isEmpty()) return cachedResult;
      CompilationOutput reused = lastSuccessfulOutput.reused();
      return new CompilationResult(Optional.of(reused), cachedResult.diagnostics());
    }

    IncrementalAnalysisPlan.History historyFor(CompilationRequest current) {
      return request.entryDocument().equals(current.entryDocument())
              && request.kind() == current.kind()
              && request.scope().equals(current.scope())
              && request.exportedSources().equals(current.exportedSources())
              && request.bindingSources().equals(current.bindingSources())
          ? snapshot.history()
          : null;
    }
  }

  private record PreparedCompilation(
      CompilationSnapshot snapshot,
      Optional<BoundProgram> resolvedProgram,
      Set<DocumentId> exportedSources,
      Map<DocumentId, ModuleSourceCoordinate> sourceCoordinates,
      IncrementalAnalysisPlan analysisPlan,
      ImportedCompilation imported) {
    private PreparedCompilation {
      java.util.Objects.requireNonNull(snapshot, "snapshot");
      resolvedProgram = java.util.Objects.requireNonNull(resolvedProgram, "resolvedProgram");
      exportedSources = Set.copyOf(exportedSources);
      sourceCoordinates = Map.copyOf(sourceCoordinates);
      java.util.Objects.requireNonNull(analysisPlan, "analysisPlan");
    }
  }

  private record CompilationProduct(
      CompilationResult compilation, CompilationSnapshot snapshot, CoreBuildHistory history) {}
}
