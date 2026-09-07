package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.language.Completion;
import dev.w0fv1.norm.language.CompletionKind;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.language.SignatureHelp;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.source.SourcePosition;
import dev.w0fv1.norm.workspace.Workspace;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import org.eclipse.lsp4j.DocumentFormattingParams;
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
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

final class DocumentService implements TextDocumentService, AutoCloseable {
  private final Workspace workspace;
  private final LanguageService language;

  DocumentService(Workspace workspace) {
    this.workspace = workspace;
    this.language = workspace.language();
  }

  void connect(LanguageClient client) {
    workspace.onDiagnostics(
        update ->
            client.publishDiagnostics(
                new PublishDiagnosticsParams(
                    update.uri(),
                    update.diagnostics().stream().map(DocumentService::diagnostic).toList(),
                    update.version())));
  }

  @Override
  public void close() {
    workspace.close();
  }

  CompletableFuture<Void> settled() {
    return workspace.settled();
  }

  CompletableFuture<String> source(String uri) {
    return workspace.source(uri);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var document = params.getTextDocument();
    workspace.update(document.getUri(), document.getVersion(), document.getText());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    if (params.getContentChanges().isEmpty()) return;
    workspace.update(
        params.getTextDocument().getUri(),
        params.getTextDocument().getVersion(),
        params.getContentChanges().getLast().getText());
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    workspace.closeDocument(params.getTextDocument().getUri());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    workspace.watchedFilesChanged(List.of(params.getTextDocument().getUri()));
  }

  void watchedFilesChanged(Collection<String> uris) {
    workspace.watchedFilesChanged(uris);
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      CompletionParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null)
                return CompletableFuture.completedFuture(Either.forLeft(List.of()));
              int offset = offset(state.source(), params.getPosition());
              List<Completion> completions =
                  language.complete(
                      state.snapshot().document(state.source().id()).orElseThrow(), offset);
              List<CompletionItem> items =
                  java.util.stream.IntStream.range(0, completions.size())
                      .mapToObj(index -> completion(completions.get(index), index, state.source()))
                      .toList();
              return CompletableFuture.completedFuture(Either.forLeft(items));
            });
  }

  @Override
  public CompletableFuture<org.eclipse.lsp4j.SignatureHelp> signatureHelp(
      SignatureHelpParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null) return CompletableFuture.completedFuture(null);
              int offset = offset(state.source(), params.getPosition());
              return CompletableFuture.completedFuture(
                  language
                      .signatureHelp(
                          state.snapshot().document(state.source().id()).orElseThrow(), offset)
                      .map(DocumentService::signatureHelp)
                      .orElse(null));
            });
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null) return CompletableFuture.completedFuture(null);
              int offset = offset(state.source(), params.getPosition());
              return CompletableFuture.completedFuture(
                  language
                      .hover(state.analysis(), offset)
                      .map(
                          info ->
                              new Hover(new MarkupContent(MarkupKind.MARKDOWN, info.markdown())))
                      .orElse(null));
            });
  }

  @Override
  public CompletableFuture<
          Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>>>
      definition(DefinitionParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null)
                return CompletableFuture.completedFuture(Either.forLeft(List.of()));
              int offset = offset(state.source(), params.getPosition());
              List<Location> locations =
                  language.definition(state.analysis(), offset).stream()
                      .map(location -> location(state.snapshot(), location))
                      .toList();
              return CompletableFuture.completedFuture(Either.forLeft(locations));
            });
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null) return CompletableFuture.completedFuture(List.of());
              int offset = offset(state.source(), params.getPosition());
              List<Location> locations =
                  language
                      .references(
                          state.analysis(), offset, params.getContext().isIncludeDeclaration())
                      .stream()
                      .map(location -> location(state.snapshot(), location))
                      .toList();
              return CompletableFuture.completedFuture(locations);
            });
  }

  @Override
  public CompletableFuture<
          Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior>>
      prepareRename(PrepareRenameParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
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
                                              range(state.snapshot(), target.location()),
                                              target.placeholder())))
                      .orElse(null));
            });
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
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
                                                  workspace.clientUri(
                                                      state.snapshot(), location.document()),
                                                  ignored -> new java.util.ArrayList<>())
                                              .add(
                                                  new TextEdit(
                                                      range(state.snapshot(), location),
                                                      rename.newName())));
                              return new WorkspaceEdit(changes);
                            })
                        .orElse(null);
                return CompletableFuture.completedFuture(edit);
              } catch (IllegalArgumentException exception) {
                return CompletableFuture.failedFuture(exception);
              }
            });
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    return workspace
        .document(params.getTextDocument().getUri())
        .thenCompose(
            state -> {
              if (state == null) return CompletableFuture.completedFuture(List.of());
              return CompletableFuture.completedFuture(
                  language
                      .format(state.source())
                      .filter(formatted -> !formatted.equals(state.source().text()))
                      .map(
                          formatted ->
                              List.of(
                                  new TextEdit(
                                      range(
                                          state.source().positionAt(0),
                                          state.source().positionAt(state.source().length())),
                                      formatted)))
                      .orElse(List.of()));
            });
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

  private static org.eclipse.lsp4j.SignatureHelp signatureHelp(SignatureHelp help) {
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
      case INTERFACE -> CompletionItemKind.Interface;
      case FUNCTION -> CompletionItemKind.Function;
      case METHOD -> CompletionItemKind.Method;
      case FIELD -> CompletionItemKind.Field;
      case PROPERTY -> CompletionItemKind.Property;
      case ENUM_VARIANT -> CompletionItemKind.EnumMember;
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

  private Location location(CompilationSnapshot snapshot, SourceLocation location) {
    return new Location(
        workspace.clientUri(snapshot, location.document()), range(snapshot, location));
  }

  private Range range(CompilationSnapshot snapshot, SourceLocation location) {
    SourceFile source =
        snapshot.document(location.document()).map(document -> document.source()).orElse(null);
    if (source == null && location.document().uri().getScheme().equals("stdlib")) {
      source =
          language
              .standardLibrarySource(location.document())
              .map(text -> SourceFile.of(location.document(), text))
              .orElseThrow(
                  () -> new IllegalStateException("standard-library source is unavailable"));
    } else if (source == null) {
      throw new IllegalStateException("source document is absent from the compilation snapshot");
    }
    return range(
        source.positionAt(location.startOffset()), source.positionAt(location.endOffset()));
  }
}
