package dev.w0fv1.norm.cli.component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
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
    implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware {
  private final DocumentService documents = new DocumentService();
  private final WorkspaceService workspace = new WorkspaceService(documents);
  private final IntConsumer exitHandler;
  private volatile int exitCode = 1;

  public LanguageServer() {
    this(ignored -> {});
  }

  LanguageServer(IntConsumer exitHandler) {
    this.exitHandler = java.util.Objects.requireNonNull(exitHandler, "exitHandler");
  }

  @Override
  public void connect(LanguageClient client) {
    documents.connect(client);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    capabilities.setCompletionProvider(new CompletionOptions(false, List.of(".")));
    capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(", ",")));
    capabilities.setHoverProvider(true);
    capabilities.setDefinitionProvider(true);
    capabilities.setReferencesProvider(true);
    capabilities.setRenameProvider(new RenameOptions(true));
    capabilities.setDocumentFormattingProvider(true);
    return CompletableFuture.completedFuture(new InitializeResult(capabilities));
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    exitCode = 0;
    return CompletableFuture.completedFuture(null);
  }

  @JsonRequest("norm/standardLibrarySource")
  public CompletableFuture<String> standardLibrarySource(String uri) {
    return CompletableFuture.completedFuture(documents.standardLibrarySource(uri));
  }

  @Override
  public void exit() {
    exitHandler.accept(exitCode);
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
