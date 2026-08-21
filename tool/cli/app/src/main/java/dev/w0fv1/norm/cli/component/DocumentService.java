package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.language.Completion;
import dev.w0fv1.norm.language.CompletionKind;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceLocation;
import dev.w0fv1.norm.value.SourcePosition;
import java.util.List;
import java.util.Map;
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
  private volatile LanguageClient client;

  void connect(LanguageClient client) {
    this.client = client;
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
    documents.remove(uri);
    LanguageClient connected = client;
    if (connected != null) {
      connected.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    DocumentState state = documents.get(params.getTextDocument().getUri());
    if (state != null) publish(params.getTextDocument().getUri(), state.analysis());
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      CompletionParams params) {
    DocumentState state = documents.get(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    int offset = offset(state.source(), params.getPosition());
    List<CompletionItem> items =
        language.complete(state.analysis(), offset).stream()
            .map(DocumentService::completion)
            .toList();
    return CompletableFuture.completedFuture(Either.forLeft(items));
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params) {
    DocumentState state = documents.get(params.getTextDocument().getUri());
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
    DocumentState state = documents.get(params.getTextDocument().getUri());
    if (state == null) return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    int offset = offset(state.source(), params.getPosition());
    List<Location> locations =
        language.definition(state.analysis(), offset).stream().map(this::location).toList();
    return CompletableFuture.completedFuture(Either.forLeft(locations));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    DocumentState state = documents.get(params.getTextDocument().getUri());
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
    DocumentState state = documents.get(params.getTextDocument().getUri());
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
    DocumentState state = documents.get(params.getTextDocument().getUri());
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
                                        location.document().uri().toString(),
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
    AnalysisResult analysis = language.analyze(source);
    documents.compute(
        uri,
        (ignored, current) ->
            current == null || version >= current.version()
                ? new DocumentState(version, source, analysis)
                : current);
    DocumentState current = documents.get(uri);
    if (current != null && current.version() == version) publish(uri, current.analysis());
  }

  private void publish(String uri, AnalysisResult analysis) {
    LanguageClient connected = client;
    if (connected == null) return;
    List<org.eclipse.lsp4j.Diagnostic> diagnostics =
        analysis.diagnostics().stream().map(DocumentService::diagnostic).toList();
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

  private static CompletionItem completion(Completion completion) {
    CompletionItem item = new CompletionItem(completion.label());
    item.setKind(kind(completion.kind()));
    item.setDetail(completion.detail());
    item.setInsertText(completion.insertText());
    if (!completion.documentation().isBlank()) item.setDocumentation(completion.documentation());
    if (completion.snippet()) item.setInsertTextFormat(InsertTextFormat.Snippet);
    return item;
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
    return new Location(location.document().uri().toString(), range(location));
  }

  private Range range(SourceLocation location) {
    DocumentState state = documents.get(location.document().uri().toString());
    if (state == null) throw new IllegalStateException("source document is not open");
    return range(
        state.source().positionAt(location.startOffset()),
        state.source().positionAt(location.endOffset()));
  }

  private record DocumentState(int version, SourceFile source, AnalysisResult analysis) {}
}
