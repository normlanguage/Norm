package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

final class LanguageServerTest {
  @Test
  void publishesCompilerDiagnosticsForOpenDocuments() {
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(
                    "file:///invalid.norm", "norm", 1, "void main() { missing(1) }")));

    assertNotNull(client.diagnostics);
    assertEquals(
        "NORM-NAME-0003", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void completesMembersForTheDeclaredContainerType() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///completion.norm";
    String text = "void main() { List values = List() values. }";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));
    CompletionParams params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(uri));
    params.setPosition(new Position(0, text.indexOf("values.") + "values.".length()));

    List<CompletionItem> items = server.getTextDocumentService().completion(params).get().getLeft();

    assertTrue(items.stream().anyMatch(item -> item.getLabel().equals("add")));
    assertTrue(items.stream().anyMatch(item -> item.getLabel().equals("removeAt")));
    assertTrue(items.stream().noneMatch(item -> item.getLabel().equals("push")));
  }

  @Test
  void publishesDiagnosticsForUntitledDocuments() {
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(
                    "untitled:Untitled-1", "norm", 1, "void main() { missing(1) }")));

    assertNotNull(client.diagnostics);
    assertEquals(
        "NORM-NAME-0003", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void exposesDefinitionReferencesAndRenameFromSemanticBindings() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///navigation.norm";
    String text = "void main() { int value = 1 print(value) }";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));
    Position position = new Position(0, text.indexOf("value)"));
    TextDocumentIdentifier document = new TextDocumentIdentifier(uri);

    var definition =
        server
            .getTextDocumentService()
            .definition(new DefinitionParams(document, position))
            .get()
            .getLeft();
    var referenceParams = new ReferenceParams(document, position, new ReferenceContext(true));
    var references = server.getTextDocumentService().references(referenceParams).get();
    var rename =
        server
            .getTextDocumentService()
            .rename(new RenameParams(document, position, "result"))
            .get();

    assertEquals(text.indexOf("value"), definition.getFirst().getRange().getStart().getCharacter());
    assertEquals(2, references.size());
    assertEquals(2, rename.getChanges().get(uri).size());
  }

  private static final class RecordingClient implements LanguageClient {
    private PublishDiagnosticsParams diagnostics;

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      this.diagnostics = diagnostics;
    }

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
        ShowMessageRequestParams requestParams) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}
  }
}
