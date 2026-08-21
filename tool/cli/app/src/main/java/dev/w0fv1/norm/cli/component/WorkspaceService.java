package dev.w0fv1.norm.cli.component;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;

final class WorkspaceService implements org.eclipse.lsp4j.services.WorkspaceService {
  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {}

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
