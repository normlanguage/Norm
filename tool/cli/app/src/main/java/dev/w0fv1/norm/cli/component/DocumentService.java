package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.language.Completion;
import dev.w0fv1.norm.language.CompletionKind;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceLocation;
import dev.w0fv1.norm.value.SourcePosition;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

final class DocumentService implements TextDocumentService {
  private final LanguageService language = new LanguageService();
  private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong revisions =
      new java.util.concurrent.atomic.AtomicLong();
  private volatile LanguageClient client;

  void connect(LanguageClient client) {
    this.client = client;
  }

  String standardLibrarySource(String uri) {
    return language
        .standardLibrarySource(DocumentId.of(uri))
        .orElseThrow(() -> new IllegalArgumentException("unknown standard-library source " + uri));
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    update(
        params.getTextDocument().getUri(),
        params.getTextDocument().getVersion(),
        params.getTextDocument().getText());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
    if (!changes.isEmpty()) {
      update(
          params.getTextDocument().getUri(),
          params.getTextDocument().getVersion(),
          changes.getLast().getText());
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    String uri = params.getTextDocument().getUri();
    DocumentState removed = remove(uri);
    LanguageClient connected = client;
    if (connected != null) {
      connected.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }
    if (removed != null && removed.projectRoot() != null) refresh(removed.projectRoot());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state != null) publish(params.getTextDocument().getUri(), state.analysis());
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      CompletionParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    int offset = offset(state.source(), params.getPosition());
    List<Completion> completions =
        language.complete(state.snapshot().document(state.source().id()).orElseThrow(), offset);
    List<CompletionItem> items =
        java.util.stream.IntStream.range(0, completions.size())
            .mapToObj(index -> completion(completions.get(index), index, state.source()))
            .toList();
    return CompletableFuture.completedFuture(Either.forLeft(items));
  }

  @Override
  public CompletableFuture<org.eclipse.lsp4j.SignatureHelp> signatureHelp(
      SignatureHelpParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(null);
    int offset = offset(state.source(), params.getPosition());
    return CompletableFuture.completedFuture(
        language
            .signatureHelp(state.snapshot().document(state.source().id()).orElseThrow(), offset)
            .map(DocumentService::signatureHelp)
            .orElse(null));
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(null);
    int offset = offset(state.source(), params.getPosition());
    return CompletableFuture.completedFuture(
        language
            .hover(state.analysis(), offset)
            .map(info -> new Hover(new MarkupContent(MarkupKind.MARKDOWN, info.markdown())))
            .orElse(null));
  }

  @Override
  public CompletableFuture<
          Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>>
      definition(DefinitionParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    int offset = offset(state.source(), params.getPosition());
    List<Location> locations =
        language.definition(state.analysis(), offset).stream().map(this::location).toList();
    return CompletableFuture.completedFuture(Either.forLeft(locations));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(List.of());
    int offset = offset(state.source(), params.getPosition());
    List<Location> locations =
        language
            .references(state.analysis(), offset, params.getContext().isIncludeDeclaration())
            .stream()
            .map(this::location)
            .toList();
    return CompletableFuture.completedFuture(locations);
  }

  @Override
  public CompletableFuture<
          Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>
      prepareRename(PrepareRenameParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(null);
    int offset = offset(state.source(), params.getPosition());
    return CompletableFuture.completedFuture(
        language
            .prepareRename(state.analysis(), offset)
            .map(
                target ->
                    Either3
                        .<Range, PrepareRenameResult,
                            org.eclipse.lsp4j.PrepareRenameDefaultBehavior>
                            forSecond(
                                new PrepareRenameResult(
                                    range(target.location()), target.placeholder())))
            .orElse(null));
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    DocumentState state = state(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(null);
    int offset = offset(state.source(), params.getPosition());
    try {
      WorkspaceEdit edit =
          language
              .rename(state.analysis(), offset, params.getNewName())
              .map(
                  rename -> {
                    Map<String, List<TextEdit>> changes = new java.util.LinkedHashMap<>();
                    rename
                        .locations()
                        .forEach(
                            location ->
                                changes
                                    .computeIfAbsent(
                                        clientUri(location.document()),
                                        ignored -> new java.util.ArrayList<>())
                                    .add(new TextEdit(range(location), rename.newName())));
                    return new WorkspaceEdit(changes);
                  })
              .orElse(null);
      return CompletableFuture.completedFuture(edit);
    } catch (IllegalArgumentException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private void update(String uri, int version, String text) {
    SourceFile source = SourceFile.of(DocumentId.of(uri), text);
    DocumentState existing = state(uri);
    if (existing != null && version < existing.version()) return;
    if (existing != null && !existing.clientUri().equals(uri)) {
      documents.remove(existing.clientUri(), existing);
    }
    if (!"file".equals(source.id().uri().getScheme())) {
      CompilationSnapshot snapshot = language.snapshot(CompilationRequest.single(source));
      AnalysisResult analysis = snapshot.analysis();
      DocumentState candidate =
          new DocumentState(
              version,
              uri,
              source,
              analysis,
              null,
              Set.of(),
              revisions.incrementAndGet(),
              snapshot);
      if (!install(uri, candidate)) return;
      publish(uri, analysis);
      return;
    }
    Path root = ProjectSession.rootOf(source.path());
    boolean manifest = source.path().getFileName().toString().equals("module.norm");
    boolean detached = !java.nio.file.Files.exists(source.path()) && !hasManifest(root);
    if (manifest || detached) {
      CompilationSnapshot snapshot = language.snapshot(CompilationRequest.single(source));
      AnalysisResult analysis = snapshot.analysis();
      DocumentState candidate =
          new DocumentState(
              version,
              uri,
              source,
              analysis,
              root,
              manifest ? Set.of(ProjectSession.normalize(source.path())) : Set.of(),
              revisions.incrementAndGet(),
              snapshot);
      if (!install(uri, candidate)) return;
      publish(uri, analysis);
      if (manifest && java.nio.file.Files.isRegularFile(source.path())) refresh(root);
      return;
    }
    Map<Path, SourceFile> openSources = openSources();
    openSources.put(ProjectSession.normalize(source.path()), source);
    ProjectSession session =
        ProjectSession.load(language, source, openSources, revisions.incrementAndGet());
    AnalysisResult analysis = session.analysis(source);
    DocumentState candidate =
        new DocumentState(
            version,
            uri,
            source,
            analysis,
            root,
            session.inputs(),
            session.revision(),
            session.snapshot());
    if (!install(uri, candidate)) return;
    publish(uri, analysis);
    for (Map.Entry<String, DocumentState> entry : List.copyOf(documents.entrySet())) {
      DocumentState state = entry.getValue();
      if (entry.getKey().equals(uri) || !root.equals(state.projectRoot())) continue;
      if (!session.inputs().contains(ProjectSession.normalize(state.source().path()))) continue;
      AnalysisResult refreshed = session.analysis(state.source());
      DocumentState refreshedState =
          new DocumentState(
              state.version(),
              state.clientUri(),
              state.source(),
              refreshed,
              root,
              session.inputs(),
              session.revision(),
              session.snapshot());
      if (documents.replace(entry.getKey(), state, refreshedState)) {
        publish(state.clientUri(), refreshed);
      }
    }
  }

  void watchedFilesChanged(Collection<String> uris) {
    Set<Path> changed =
        uris.stream()
            .map(DocumentService::filePath)
            .flatMap(Optional::stream)
            .collect(java.util.stream.Collectors.toSet());
    documents.values().stream()
        .map(DocumentState::projectRoot)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .filter(
            root ->
                changed.stream()
                    .anyMatch(path -> path.startsWith(root) || sessionInputs(root).contains(path)))
        .toList()
        .forEach(this::refresh);
  }

  private void refresh(Path root) {
    List<DocumentState> states =
        documents.values().stream()
            .filter(state -> root.equals(state.projectRoot()))
            .filter(state -> !state.source().path().getFileName().toString().equals("module.norm"))
            .toList();
    List<DocumentState> remaining = new java.util.ArrayList<>(states);
    while (!remaining.isEmpty()) {
      ProjectSession session =
          ProjectSession.load(
              language, remaining.getFirst().source(), openSources(), revisions.incrementAndGet());
      List<DocumentState> members =
          remaining.stream()
              .filter(
                  state ->
                      session.inputs().contains(ProjectSession.normalize(state.source().path())))
              .toList();
      for (DocumentState state : members) {
        AnalysisResult analysis = session.analysis(state.source());
        DocumentState installed =
            documents.computeIfPresent(
                state.clientUri(),
                (ignored, current) -> {
                  if (current.revision() > session.revision()) return current;
                  return new DocumentState(
                      current.version(),
                      current.clientUri(),
                      current.source(),
                      analysis,
                      session.root(),
                      session.inputs(),
                      session.revision(),
                      session.snapshot());
                });
        if (installed != null && installed.revision() == session.revision()) {
          publish(state.clientUri(), analysis);
        }
      }
      remaining.removeAll(members);
    }
  }

  private Map<Path, SourceFile> openSources() {
    Map<Path, SourceFile> result = new java.util.LinkedHashMap<>();
    documents.values().stream()
        .filter(state -> "file".equals(state.source().id().uri().getScheme()))
        .filter(state -> !state.source().path().getFileName().toString().equals("module.norm"))
        .forEach(
            state -> result.put(ProjectSession.normalize(state.source().path()), state.source()));
    return result;
  }

  private boolean install(String uri, DocumentState candidate) {
    return documents.compute(
            uri,
            (ignored, current) -> {
              if (current == null) return candidate;
              if (current.version() > candidate.version()) return current;
              return current.revision() > candidate.revision() ? current : candidate;
            })
        == candidate;
  }

  private Set<Path> sessionInputs(Path root) {
    return documents.values().stream()
        .filter(state -> root.equals(state.projectRoot()))
        .findFirst()
        .map(DocumentState::sourcePaths)
        .orElse(Set.of());
  }

  private static boolean hasManifest(Path root) {
    return root != null && java.nio.file.Files.isRegularFile(root.resolve("module.norm"));
  }

  private void publish(String uri, AnalysisResult analysis) {
    LanguageClient connected = client;
    if (connected == null) return;
    List<org.eclipse.lsp4j.Diagnostic> diagnostics =
        analysis.diagnostics().stream()
            .filter(
                diagnostic -> diagnostic.primarySpan().source().id().uri().toString().equals(uri))
            .map(DocumentService::diagnostic)
            .toList();
    connected.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
  }

  private static org.eclipse.lsp4j.Diagnostic diagnostic(Diagnostic diagnostic) {
    org.eclipse.lsp4j.Diagnostic converted = new org.eclipse.lsp4j.Diagnostic();
    converted.setRange(range(diagnostic.primarySpan().start(), diagnostic.primarySpan().end()));
    converted.setSeverity(
        switch (diagnostic.severity()) {
          case ERROR -> DiagnosticSeverity.Error;
          case WARNING -> DiagnosticSeverity.Warning;
          case INFO -> DiagnosticSeverity.Information;
        });
    converted.setSource("norm");
    converted.setCode(diagnostic.code().value());
    converted.setMessage(diagnostic.message());
    return converted;
  }

  private static CompletionItem completion(Completion completion, int index, SourceFile source) {
    CompletionItem item = new CompletionItem(completion.label());
    item.setKind(kind(completion.kind()));
    item.setDetail(completion.detail());
    item.setInsertText(completion.insertText());
    item.setFilterText(completion.label());
    item.setSortText("%08d".formatted(index));
    item.setPreselect(index == 0);
    completion
        .textEdit()
        .ifPresent(
            edit ->
                item.setTextEdit(
                    Either.forLeft(
                        new TextEdit(
                            range(
                                source.positionAt(edit.location().startOffset()),
                                source.positionAt(edit.location().endOffset())),
                            edit.newText()))));
    if (!completion.additionalTextEdits().isEmpty()) {
      item.setAdditionalTextEdits(
          completion.additionalTextEdits().stream()
              .map(
                  edit ->
                      new TextEdit(
                          range(
                              source.positionAt(edit.location().startOffset()),
                              source.positionAt(edit.location().endOffset())),
                          edit.newText()))
              .toList());
    }
    if (!completion.documentation().isBlank()) item.setDocumentation(completion.documentation());
    if (completion.snippet()) item.setInsertTextFormat(InsertTextFormat.Snippet);
    return item;
  }

  private static org.eclipse.lsp4j.SignatureHelp signatureHelp(
      dev.w0fv1.norm.language.SignatureHelp help) {
    return new org.eclipse.lsp4j.SignatureHelp(
        help.signatures().stream()
            .map(
                signature ->
                    new org.eclipse.lsp4j.SignatureInformation(
                        signature.label(),
                        signature.documentation(),
                        signature.parameters().stream()
                            .map(
                                parameter ->
                                    new org.eclipse.lsp4j.ParameterInformation(
                                        parameter.label(), parameter.documentation()))
                            .toList()))
            .toList(),
        help.activeSignature(),
        help.activeParameter());
  }

  private static CompletionItemKind kind(CompletionKind kind) {
    return switch (kind) {
      case KEYWORD -> CompletionItemKind.Keyword;
      case TYPE -> CompletionItemKind.Class;
      case FUNCTION -> CompletionItemKind.Function;
      case METHOD -> CompletionItemKind.Method;
      case FIELD -> CompletionItemKind.Field;
      case PROPERTY -> CompletionItemKind.Property;
      case ENUM_MEMBER -> CompletionItemKind.EnumMember;
      case VARIABLE -> CompletionItemKind.Variable;
      case SNIPPET -> CompletionItemKind.Snippet;
    };
  }

  private static int offset(SourceFile source, Position position) {
    return source.offsetAt(position.getLine(), position.getCharacter());
  }

  private static Range range(SourcePosition start, SourcePosition end) {
    return new Range(
        new Position(start.line() - 1, start.column() - 1),
        new Position(end.line() - 1, end.column() - 1));
  }

  private Location location(SourceLocation location) {
    return new Location(clientUri(location.document()), range(location));
  }

  private Range range(SourceLocation location) {
    DocumentState state = state(location.document().uri().toString());
    SourceFile source;
    if (state != null) {
      source = state.source();
    } else if (location.document().uri().getScheme().equals("stdlib")) {
      source =
          language
              .standardLibrarySource(location.document())
              .map(text -> SourceFile.of(location.document(), text))
              .orElseThrow(
                  () -> new IllegalStateException("standard-library source is unavailable"));
    } else {
      try {
        source = SourceFile.read(Path.of(location.document().uri()));
      } catch (java.io.IOException | IllegalArgumentException exception) {
        throw new IllegalStateException("source document is unavailable", exception);
      }
    }
    return range(
        source.positionAt(location.startOffset()), source.positionAt(location.endOffset()));
  }

  private DocumentState state(String uri) {
    DocumentState direct = documents.get(uri);
    if (direct != null) return direct;
    Optional<Path> path = filePath(uri);
    if (path.isEmpty()) return null;
    return documents.values().stream()
        .filter(state -> "file".equals(state.source().id().uri().getScheme()))
        .filter(state -> ProjectSession.normalize(state.source().path()).equals(path.orElseThrow()))
        .findFirst()
        .orElse(null);
  }

  private DocumentState remove(String uri) {
    DocumentState direct = documents.remove(uri);
    if (direct != null) return direct;
    DocumentState equivalent = state(uri);
    if (equivalent != null) documents.remove(equivalent.clientUri(), equivalent);
    return equivalent;
  }

  private String clientUri(DocumentId document) {
    DocumentState state = state(document.uri().toString());
    return state == null ? document.uri().toString() : state.clientUri();
  }

  private static Optional<Path> filePath(String uri) {
    try {
      URI parsed = URI.create(uri);
      return "file".equalsIgnoreCase(parsed.getScheme())
          ? Optional.of(ProjectSession.normalize(Path.of(parsed)))
          : Optional.empty();
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private record DocumentState(
      int version,
      String clientUri,
      SourceFile source,
      AnalysisResult analysis,
      Path projectRoot,
      Set<Path> sourcePaths,
      long revision,
      CompilationSnapshot snapshot) {
    private DocumentState {
      sourcePaths = Set.copyOf(sourcePaths);
    }
  }
}
