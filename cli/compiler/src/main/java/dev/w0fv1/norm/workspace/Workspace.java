package dev.w0fv1.norm.workspace;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CancellationToken;
import dev.w0fv1.norm.frontend.CompilationControl;
import dev.w0fv1.norm.frontend.CompilationLimits;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.CompilationRequest;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class Workspace implements AutoCloseable {
  private final Object gate = new Object();
  private final LanguageService language;
  private final ProjectLoader projects;
  private final AnalysisScheduler<String> planner = new AnalysisScheduler<>();
  private final AnalysisScheduler<String> analyses = new AnalysisScheduler<>();
  private final ExecutorService publications =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
  private final Map<String, OpenDocument> open = new LinkedHashMap<>();
  private final Map<String, Batch> requests = new LinkedHashMap<>();
  private final Set<String> changed = new LinkedHashSet<>();
  private volatile Map<String, WorkspaceDocument> documents = Map.of();
  private volatile Consumer<Diagnostics> listener = ignored -> {};
  private long revision;
  private boolean closed;

  public Workspace(ProjectEnvironment environment) {
    language = new LanguageService(environment.compilerSession());
    projects = environment.projectLoader();
  }

  public LanguageService language() {
    return language;
  }

  public void onDiagnostics(Consumer<Diagnostics> listener) {
    this.listener = java.util.Objects.requireNonNull(listener, "listener");
  }

  public void update(String uri, int version, String text) {
    SourceFile source =
        filePath(uri)
            .map(path -> SourceFile.of(path, text))
            .orElseGet(() -> SourceFile.of(DocumentId.of(uri), text));
    synchronized (gate) {
      if (closed) throw new IllegalStateException("workspace is closed");
      OpenDocument previous =
          open.values().stream()
              .filter(value -> value.source().id().equals(source.id()))
              .findFirst()
              .orElse(null);
      if (previous != null && previous.version() >= version) return;
      if (previous != null && !previous.uri().equals(uri)) {
        open.remove(previous.uri());
        var next = new LinkedHashMap<>(documents);
        next.remove(previous.uri());
        documents = Map.copyOf(next);
        publish(
            new Diagnostics(previous.uri(), null, List.of()),
            () -> !open.containsKey(previous.uri()));
      }
      open.put(uri, new OpenDocument(uri, version, source, ++revision));
      changed.add(uri);
      planner.submit("workspace", ignored -> reconcile());
    }
  }

  public void closeDocument(String uri) {
    synchronized (gate) {
      OpenDocument previous =
          open.values().stream()
              .filter(
                  value ->
                      value.uri().equals(uri) || value.source().id().equals(DocumentId.of(uri)))
              .findFirst()
              .orElse(null);
      if (previous == null) return;
      open.remove(previous.uri());
      var next = new LinkedHashMap<>(documents);
      next.remove(previous.uri());
      documents = Map.copyOf(next);
      ++revision;
      changed.add(uri);
      publish(
          new Diagnostics(previous.uri(), null, List.of()),
          () -> !open.containsKey(previous.uri()));
      planner.submit("workspace", ignored -> reconcile());
    }
  }

  public void watchedFilesChanged(Collection<String> uris) {
    synchronized (gate) {
      if (closed) return;
      changed.addAll(uris);
      ++revision;
      planner.submit("workspace", ignored -> reconcile());
    }
  }

  private void reconcile() {
    Map<String, OpenDocument> captured;
    Set<String> dirty;
    long epoch;
    synchronized (gate) {
      if (closed) return;
      captured = Map.copyOf(open);
      dirty = Set.copyOf(changed);
      epoch = revision;
    }
    Map<Path, SourceFile> overlays = new LinkedHashMap<>();
    captured
        .values()
        .forEach(
            document ->
                filePath(document.uri()).ifPresent(path -> overlays.put(path, document.source())));
    Map<String, Map<String, OpenDocument>> groups = new LinkedHashMap<>();
    Map<String, Path> roots = new LinkedHashMap<>();
    for (var document : captured.values()) {
      Path root =
          filePath(document.uri())
              .map(path -> projects.projectRoot(document.source(), overlays.values()))
              .orElse(null);
      String key = root == null ? document.uri() : root.toUri().toString();
      roots.put(key, root);
      groups.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(document.uri(), document);
    }
    Set<Path> paths =
        dirty.stream()
            .map(Workspace::filePath)
            .flatMap(Optional::stream)
            .collect(java.util.stream.Collectors.toSet());
    synchronized (gate) {
      if (closed || epoch != revision) return;
      changed.clear();
      for (String key : List.copyOf(requests.keySet())) {
        if (!groups.containsKey(key)) {
          requests.remove(key);
          analyses.cancel(key);
        }
      }
      groups.forEach(
          (key, members) -> {
            Batch previous = requests.get(key);
            Path root = roots.get(key);
            boolean invalidated =
                members.keySet().stream().anyMatch(dirty::contains)
                    || paths.stream().anyMatch(path -> root != null && path.startsWith(root))
                    || members.keySet().stream()
                        .map(documents::get)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(
                            document -> document.sourcePaths().stream().anyMatch(paths::contains));
            if (previous != null && previous.members.equals(members) && !invalidated) return;
            var batch = new Batch(key, root, Map.copyOf(members), Map.copyOf(overlays));
            requests.put(key, batch);
            batch.completion = analyses.submit(key, cancellation -> analyze(batch, cancellation));
          });
    }
  }

  private void analyze(Batch batch, CancellationToken cancellation) throws Exception {
    Map<String, WorkspaceDocument> result = new LinkedHashMap<>();
    var remaining = new ArrayList<>(batch.members.values());
    var control = new CompilationControl(cancellation, CompilationLimits.standard());
    while (!remaining.isEmpty()) {
      if (cancellation.isCancellationRequested()) return;
      OpenDocument document = remaining.removeFirst();
      SourceFile source = document.source();
      Optional<DocumentId> virtual = VirtualDocumentUri.decode(document.uri());
      CompilationSnapshot snapshot;
      AnalysisResult analysis;
      Set<Path> inputs = Set.of();
      Path root = batch.root;
      if (virtual.isPresent()) {
        snapshot =
            snapshotState(virtual.orElseThrow())
                .orElseThrow(
                    () -> new IllegalArgumentException("unknown virtual source " + document.uri()))
                .snapshot();
        source = snapshot.document(virtual.orElseThrow()).orElseThrow().source();
        analysis = snapshot.analysis(source.id());
      } else if (!"file".equalsIgnoreCase(source.id().uri().getScheme())) {
        snapshot =
            "stdlib".equals(source.id().uri().getScheme())
                ? language.standardLibrarySnapshot(List.of(), source.id(), control)
                : language.snapshot(CompilationRequest.single(source), control);
        analysis = snapshot.analysis();
        source = analysis.semanticModel().source();
      } else {
        Path path = ProjectSession.normalize(source.path());
        if (ProjectLoader.isModuleSource(source)) {
          snapshot = projects.analyzeModule(source);
          analysis = snapshot.analysis(source.id());
          inputs = Set.of(path);
          if (!analysis.hasErrors()) {
            try {
              projects.evaluateModule(source);
            } catch (java.io.IOException exception) {
              var diagnostics = new ArrayList<>(analysis.diagnostics());
              diagnostics.add(
                  Diagnostic.error(
                      new DiagnosticCode("NORM-PROJECT-0001"),
                      exception.getMessage(),
                      new SourceSpan(source, 0, Math.min(1, source.length()))));
              analysis =
                  new AnalysisResult(analysis.semanticModel(), analysis.entryPoint(), diagnostics);
            }
          }
        } else {
          ProjectSession session =
              ProjectSession.load(
                  language, projects, source, batch.overlays, document.revision(), cancellation);
          snapshot = session.snapshot();
          analysis = session.analysis(source);
          source = session.source(source);
          inputs = session.inputs();
          root = session.root();
          for (var member : List.copyOf(remaining)) {
            if (ProjectLoader.isModuleSource(member.source()) || !session.contains(member.source()))
              continue;
            result.put(
                member.uri(),
                new WorkspaceDocument(
                    member.version(),
                    member.uri(),
                    session.source(member.source()),
                    session.analysis(member.source()),
                    root,
                    inputs,
                    member.revision(),
                    snapshot));
            remaining.remove(member);
          }
        }
      }
      result.put(
          document.uri(),
          new WorkspaceDocument(
              document.version(),
              document.uri(),
              source,
              analysis,
              root,
              inputs,
              document.revision(),
              snapshot));
    }
    synchronized (gate) {
      if (closed || cancellation.isCancellationRequested() || requests.get(batch.key) != batch)
        return;
      if (batch.members.entrySet().stream()
          .anyMatch(entry -> open.get(entry.getKey()) != entry.getValue())) return;
      var next = new LinkedHashMap<>(documents);
      next.putAll(result);
      documents = Map.copyOf(next);
      for (var document : result.values()) {
        var diagnostics =
            document.analysis().diagnostics().stream()
                .filter(value -> value.primarySpan().source().id().equals(document.source().id()))
                .toList();
        publish(
            new Diagnostics(document.clientUri(), document.version(), diagnostics),
            () ->
                requests.get(batch.key) == batch
                    && batch.members.entrySet().stream()
                        .allMatch(entry -> open.get(entry.getKey()) == entry.getValue()));
      }
    }
  }

  private void publish(Diagnostics diagnostics, java.util.function.BooleanSupplier current) {
    publications.execute(
        () -> {
          synchronized (gate) {
            if (closed || !current.getAsBoolean()) return;
          }
          listener.accept(diagnostics);
        });
  }

  public CompletableFuture<Void> settled() {
    long epoch;
    synchronized (gate) {
      epoch = revision;
    }
    return planner
        .settled()
        .thenCompose(ignored -> analyses.settled())
        .thenCompose(
            ignored -> {
              synchronized (gate) {
                if (revision != epoch) return settled();
                return CompletableFuture.allOf(
                    requests.values().stream()
                        .map(batch -> batch.completion)
                        .toArray(CompletableFuture[]::new));
              }
            })
        .thenCompose(ignored -> CompletableFuture.runAsync(() -> {}, publications))
        .thenCompose(
            ignored -> {
              synchronized (gate) {
                return revision == epoch ? CompletableFuture.completedFuture(null) : settled();
              }
            });
  }

  public CompletableFuture<WorkspaceDocument> document(String uri) {
    return planner
        .settled()
        .thenCompose(
            ignored -> {
              Batch batch;
              synchronized (gate) {
                if (closed)
                  return CompletableFuture.failedFuture(
                      new IllegalStateException("workspace is closed"));
                batch =
                    requests.values().stream()
                        .filter(
                            value ->
                                value.members.containsKey(uri)
                                    || value.members.values().stream()
                                        .anyMatch(
                                            document ->
                                                document.source().id().equals(DocumentId.of(uri))))
                        .findFirst()
                        .orElse(null);
              }
              if (batch == null) return CompletableFuture.completedFuture(state(uri));
              return batch
                  .completion
                  .handle(
                      (completed, failure) -> {
                        synchronized (gate) {
                          if (requests.get(batch.key) != batch
                              || batch.members.entrySet().stream()
                                  .anyMatch(entry -> open.get(entry.getKey()) != entry.getValue()))
                            return document(uri);
                        }
                        if (failure != null)
                          return CompletableFuture.<WorkspaceDocument>failedFuture(failure);
                        return CompletableFuture.completedFuture(state(uri));
                      })
                  .thenCompose(java.util.function.Function.identity());
            });
  }

  public CompletableFuture<String> source(String uri) {
    if ("stdlib".equals(URI.create(uri).getScheme()))
      return language
          .standardLibrarySource(DocumentId.of(uri))
          .map(CompletableFuture::completedFuture)
          .orElseGet(
              () ->
                  CompletableFuture.failedFuture(
                      new IllegalArgumentException("unknown standard-library source " + uri)));
    Optional<DocumentId> target = VirtualDocumentUri.decode(uri);
    if (target.isEmpty())
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("unknown virtual source " + uri));
    DocumentId document = target.orElseThrow();
    return planner
        .settled()
        .thenCompose(
            ignored -> {
              Optional<WorkspaceDocument> owner = snapshotState(document);
              if (owner.isEmpty())
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("unknown virtual source " + uri));
              return document(owner.orElseThrow().clientUri())
                  .thenApply(
                      current -> {
                        if (current == null)
                          throw new IllegalArgumentException("unknown virtual source " + uri);
                        return current
                            .snapshot()
                            .document(document)
                            .orElseThrow(
                                () -> new IllegalArgumentException("unknown virtual source " + uri))
                            .source()
                            .text();
                      });
            });
  }

  public String clientUri(CompilationSnapshot snapshot, DocumentId document) {
    var state = state(document.uri().toString());
    if (state != null) return state.clientUri();
    if (!"file".equalsIgnoreCase(document.uri().getScheme())
        || Files.isRegularFile(Path.of(document.uri()))) return document.uri().toString();
    return snapshot.document(document).isPresent()
        ? VirtualDocumentUri.encode(document)
        : document.uri().toString();
  }

  private WorkspaceDocument state(String uri) {
    var captured = documents;
    WorkspaceDocument direct = captured.get(uri);
    if (direct != null) return direct;
    DocumentId identity =
        filePath(uri).map(path -> new DocumentId(path.toUri())).orElseGet(() -> DocumentId.of(uri));
    return captured.values().stream()
        .filter(state -> state.source().id().equals(identity))
        .findFirst()
        .orElse(null);
  }

  private Optional<WorkspaceDocument> snapshotState(DocumentId document) {
    return documents.values().stream()
        .filter(state -> state.snapshot().document(document).isPresent())
        .max(java.util.Comparator.comparingLong(WorkspaceDocument::revision));
  }

  private static Optional<Path> filePath(String uri) {
    URI parsed = URI.create(uri);
    return "file".equalsIgnoreCase(parsed.getScheme())
        ? Optional.of(ProjectSession.normalize(Path.of(parsed)))
        : Optional.empty();
  }

  @Override
  public void close() {
    synchronized (gate) {
      if (closed) return;
      closed = true;
      open.clear();
      documents = Map.of();
      requests.clear();
    }
    try {
      planner.close();
    } finally {
      try {
        analyses.close();
      } finally {
        try {
          publications.close();
        } finally {
          try {
            language.close();
          } finally {
            projects.close();
          }
        }
      }
    }
  }

  public record Diagnostics(String uri, Integer version, List<Diagnostic> diagnostics) {
    public Diagnostics {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  private record OpenDocument(String uri, int version, SourceFile source, long revision) {}

  private static final class Batch {
    private final String key;
    private final Path root;
    private final Map<String, OpenDocument> members;
    private final Map<Path, SourceFile> overlays;
    private CompletableFuture<Void> completion;

    private Batch(
        String key, Path root, Map<String, OpenDocument> members, Map<Path, SourceFile> overlays) {
      this.key = key;
      this.root = root;
      this.members = members;
      this.overlays = overlays;
    }
  }
}
