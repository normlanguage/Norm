package dev.w0fv1.norm.cli.component;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;

final class WorkspaceService implements org.eclipse.lsp4j.services.WorkspaceService {
  private final DocumentService documents;

  WorkspaceService(DocumentService documents) {
    this.documents = java.util.Objects.requireNonNull(documents, "documents");
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {}

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    documents.watchedFilesChanged(
        params.getChanges().stream().map(org.eclipse.lsp4j.FileEvent::getUri).toList());
  }
}
