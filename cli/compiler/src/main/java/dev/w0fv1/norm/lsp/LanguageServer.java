package dev.w0fv1.norm.lsp;

import dev.w0fv1.norm.workspace.Workspace;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;

final class LanguageServer
    implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware, AutoCloseable {
  private final DocumentService documents;
  private final WorkspaceService workspace;
  private LanguageClient client;
  private boolean dynamicWatches;
  private boolean relativeWatches;
  private boolean initialized;
  private volatile boolean closed;
  private List<dev.w0fv1.norm.value.InputWatch> watches = List.of();
  private List<dev.w0fv1.norm.value.InputWatch> registeredWatches = List.of();
  private CompletableFuture<Void> watchChanges = CompletableFuture.completedFuture(null);
  private volatile boolean exited;
  private volatile int exitCode = 1;

  LanguageServer(Workspace workspace) {
    documents = new DocumentService(java.util.Objects.requireNonNull(workspace, "workspace"));
    this.workspace = new WorkspaceService(documents);
    workspace.onInputs(this::watchInputs);
  }

  boolean exited() {
    return exited;
  }

  int exitCode() {
    return exitCode;
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    watchInputs(List.of());
    documents.close();
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
    documents.connect(client);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    var workspaceCapabilities =
        params.getCapabilities() == null ? null : params.getCapabilities().getWorkspace();
    var watchCapabilities =
        workspaceCapabilities == null ? null : workspaceCapabilities.getDidChangeWatchedFiles();
    dynamicWatches =
        watchCapabilities != null
            && Boolean.TRUE.equals(watchCapabilities.getDynamicRegistration());
    relativeWatches =
        watchCapabilities != null
            && Boolean.TRUE.equals(watchCapabilities.getRelativePatternSupport());
    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    capabilities.setCompletionProvider(new CompletionOptions(false, List.of(".", "@")));
    capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(", ",")));
    capabilities.setHoverProvider(true);
    capabilities.setDefinitionProvider(true);
    capabilities.setReferencesProvider(true);
    capabilities.setRenameProvider(new RenameOptions(true));
    capabilities.setDocumentFormattingProvider(true);
    capabilities.setCodeLensProvider(new org.eclipse.lsp4j.CodeLensOptions(false));
    return CompletableFuture.completedFuture(new InitializeResult(capabilities));
  }

  @Override
  public synchronized void initialized(org.eclipse.lsp4j.InitializedParams params) {
    initialized = true;
    watchInputs(watches);
  }

  private synchronized void watchInputs(List<dev.w0fv1.norm.value.InputWatch> inputs) {
    if (closed && !inputs.isEmpty()) return;
    watches = List.copyOf(inputs);
    if (!initialized || !dynamicWatches || client == null) return;
    var next = watches;
    watchChanges =
        watchChanges
            .handle((ignored, failure) -> null)
            .thenCompose(
                ignored -> {
                  if (closed && !next.isEmpty()) return CompletableFuture.completedFuture(null);
                  if (registeredWatches.equals(next))
                    return CompletableFuture.completedFuture(null);
                  var removal =
                      registeredWatches.isEmpty()
                          ? CompletableFuture.<Void>completedFuture(null)
                          : client
                              .unregisterCapability(
                                  new org.eclipse.lsp4j.UnregistrationParams(
                                      List.of(
                                          new org.eclipse.lsp4j.Unregistration(
                                              "norm-project-inputs",
                                              "workspace/didChangeWatchedFiles"))))
                              .thenRun(() -> registeredWatches = List.of());
                  return removal.thenCompose(
                      removed -> {
                        if (closed || next.isEmpty())
                          return CompletableFuture.completedFuture(null);
                        var watchers =
                            next.stream()
                                .map(
                                    watch -> {
                                      var watcher = new org.eclipse.lsp4j.FileSystemWatcher();
                                      if (relativeWatches)
                                        watcher.setGlobPattern(
                                            new org.eclipse.lsp4j.RelativePattern(
                                                org.eclipse.lsp4j.jsonrpc.messages.Either.forRight(
                                                    watch.root().toUri().toString()),
                                                watch.pattern()));
                                      else watcher.setGlobPattern(watch.absolutePattern());
                                      return watcher;
                                    })
                                .toList();
                        return client
                            .registerCapability(
                                new org.eclipse.lsp4j.RegistrationParams(
                                    List.of(
                                        new org.eclipse.lsp4j.Registration(
                                            "norm-project-inputs",
                                            "workspace/didChangeWatchedFiles",
                                            new org.eclipse.lsp4j
                                                .DidChangeWatchedFilesRegistrationOptions(
                                                watchers)))))
                            .thenRun(() -> registeredWatches = next);
                      });
                })
            .whenComplete(
                (ignored, failure) -> {
                  if (failure != null)
                    client.logMessage(
                        new org.eclipse.lsp4j.MessageParams(
                            org.eclipse.lsp4j.MessageType.Error,
                            "Unable to watch project inputs: " + failure.getMessage()));
                });
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    exitCode = 0;
    close();
    return CompletableFuture.completedFuture(null);
  }

  @JsonRequest("norm/source")
  public CompletableFuture<String> source(String uri) {
    return documents.source(uri);
  }

  @Override
  public void exit() {
    exited = true;
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return documents;
  }

  @Override
  public org.eclipse.lsp4j.services.WorkspaceService getWorkspaceService() {
    return workspace;
  }
}
