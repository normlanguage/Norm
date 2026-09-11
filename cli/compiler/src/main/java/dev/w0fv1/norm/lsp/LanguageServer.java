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
  private volatile boolean exited;
  private volatile int exitCode = 1;

  LanguageServer(Workspace workspace) {
    documents = new DocumentService(java.util.Objects.requireNonNull(workspace, "workspace"));
    this.workspace = new WorkspaceService(documents);
  }

  boolean exited() {
    return exited;
  }

  int exitCode() {
    return exitCode;
  }

  @Override
  public void close() {
    documents.close();
  }

  @Override
  public void connect(LanguageClient client) {
    documents.connect(client);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
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
  public CompletableFuture<Object> shutdown() {
    exitCode = 0;
    documents.close();
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
