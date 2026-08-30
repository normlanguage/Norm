package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.stdlib.StandardLibrary;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LanguageServerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void advertisesAndServesWholeDocumentFormatting() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "untitled:formatting";
    String text = "public main(){printLine(1)}";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(new TextDocumentIdentifier(uri));
    params.setOptions(new FormattingOptions(2, true));

    var edits = server.getTextDocumentService().formatting(params).get();

    assertNotNull(
        server
            .initialize(new org.eclipse.lsp4j.InitializeParams())
            .get()
            .getCapabilities()
            .getDocumentFormattingProvider());
    assertEquals(1, edits.size());
    assertEquals("main() {\n  printLine(1)\n}\n", edits.getFirst().getNewText());
    assertEquals(new Position(0, 0), edits.getFirst().getRange().getStart());
    assertEquals(new Position(0, text.length()), edits.getFirst().getRange().getEnd());
  }

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
                    "file:///invalid.norm", "norm", 1, "Void main() { missing(1) }")));

    assertNotNull(client.diagnostics);
    assertEquals(
        "NORM-NAME-0003", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void keepsStandaloneOpenDocumentsInSeparateCompilationSessions() throws Exception {
    Path first = temporaryDirectory.resolve("First.norm");
    Path second = temporaryDirectory.resolve("Second.norm");
    String source = "Void main() {}";
    Files.writeString(first, source);
    Files.writeString(second, source);
    String firstUri = first.toUri().toString();
    String secondUri = second.toUri().toString();
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(firstUri, "norm", 1, source)));
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(secondUri, "norm", 1, source)));
    server
        .getWorkspaceService()
        .didChangeWatchedFiles(
            new DidChangeWatchedFilesParams(
                List.of(
                    new FileEvent(firstUri, FileChangeType.Changed),
                    new FileEvent(secondUri, FileChangeType.Changed))));

    assertTrue(client.diagnosticsByUri.get(firstUri).getDiagnostics().isEmpty());
    assertTrue(client.diagnosticsByUri.get(secondUri).getDiagnostics().isEmpty());
  }

  @Test
  void completesMembersForTheDeclaredContainerType() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///completion.norm";
    String text = "Void main() { List<Integer> values = List<Integer>() values. }";
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
  void servesSignatureHelpForIncompleteCalls() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///signature.norm";
    String text = "Void consume(String value, Integer count) {} Void main() { consume(";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));

    var help =
        server
            .getTextDocumentService()
            .signatureHelp(
                new SignatureHelpParams(
                    new TextDocumentIdentifier(uri), new Position(0, text.length())))
            .get();

    assertEquals(
        "Void consume(String value, Integer count)", help.getSignatures().getFirst().getLabel());
    assertEquals(0, help.getActiveParameter());
    assertEquals(
        List.of("(", ","),
        server
            .initialize(new org.eclipse.lsp4j.InitializeParams())
            .get()
            .getCapabilities()
            .getSignatureHelpProvider()
            .getTriggerCharacters());
  }

  @Test
  void preservesSemanticCompletionRankingInTheProtocol() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///ranked-completion.norm";
    String text =
        "Void main() { String label = \"ready\" Integer count = 1 String result = label }";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));
    int offset = text.lastIndexOf("label");
    CompletionParams params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(uri));
    params.setPosition(new Position(0, offset));

    List<CompletionItem> items = server.getTextDocumentService().completion(params).get().getLeft();
    CompletionItem label =
        items.stream().filter(item -> item.getLabel().equals("label")).findFirst().orElseThrow();
    CompletionItem count =
        items.stream().filter(item -> item.getLabel().equals("count")).findFirst().orElseThrow();

    assertTrue(label.getSortText().compareTo(count.getSortText()) < 0);
    assertTrue(label.getPreselect());
    assertEquals("label", label.getFilterText());
  }

  @Test
  void servesReadOnlyStandardLibrarySources() throws Exception {
    LanguageServer server = new LanguageServer();

    String source = server.standardLibrarySource("stdlib:/std/math/integer.norm").get();

    assertTrue(source.contains("Integer clamp"));
  }

  @Test
  void analyzesStandardLibraryVirtualDocumentsWithoutMakingThemEditable() throws Exception {
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);
    String uri = "stdlib:/std/math/integer.norm";
    String source = server.standardLibrarySource(uri).get();
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, source)));
    List<String> lines = source.lines().toList();
    int line =
        java.util.stream.IntStream.range(0, lines.size())
            .filter(index -> lines.get(index).contains("Integer max("))
            .findFirst()
            .orElseThrow();
    Position position = new Position(line, lines.get(line).indexOf("max"));

    var hover =
        server
            .getTextDocumentService()
            .hover(new org.eclipse.lsp4j.HoverParams(new TextDocumentIdentifier(uri), position))
            .get();
    var rename =
        server
            .getTextDocumentService()
            .rename(new RenameParams(new TextDocumentIdentifier(uri), position, "maximum"))
            .get();

    assertTrue(client.diagnostics.getDiagnostics().isEmpty());
    assertNotNull(hover);
    assertTrue(hover.getContents().getRight().getValue().contains("Integer max"));
    assertNull(rename);
  }

  @Test
  void analyzesEditableStandardLibrarySourcesAsPreludeOverlays() throws Exception {
    Path root = temporaryDirectory.resolve("norm/stdlib");
    Path module = root.resolve("std/module.norm");
    Path sourcePath = root.resolve("std/collections/sequences.norm");
    Files.createDirectories(sourcePath.getParent());
    Files.writeString(module, StandardLibrary.moduleSource().text());
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);
    String uri = sourcePath.toUri().toString();
    String source = server.standardLibrarySource("stdlib:/std/collections/sequences.norm").get();

    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, source)));

    assertTrue(client.diagnostics.getDiagnostics().isEmpty());

    String invalid = source.replaceFirst("return true", "return missing");
    server
        .getTextDocumentService()
        .didChange(
            new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                List.of(new TextDocumentContentChangeEvent(invalid))));

    assertTrue(
        client.diagnostics.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getCode().getLeft().equals("NORM-NAME-0003")));
  }

  @Test
  void navigatesToStandardLibrarySources() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "untitled:stdlib-navigation";
    String text = "import std.math.max Void main() { printLine(max(left: 1, right: 2)) }";
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));

    var definition =
        server
            .getTextDocumentService()
            .definition(
                new DefinitionParams(
                    new TextDocumentIdentifier(uri), new Position(0, text.lastIndexOf("max"))))
            .get()
            .getLeft();

    assertEquals("stdlib", java.net.URI.create(definition.getFirst().getUri()).getScheme());
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
                    "untitled:Untitled-1", "norm", 1, "Void main() { missing(1) }")));

    assertNotNull(client.diagnostics);
    assertEquals(
        "NORM-NAME-0003", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void analyzesModuleDescriptors() {
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(
                    "file:///module.norm",
                    "norm",
                    1,
                    "Module module() { return module(name: \"sample\", version: 1, exports: []) }")));

    assertNotNull(client.diagnostics);
    assertTrue(client.diagnostics.getDiagnostics().isEmpty());
  }

  @Test
  void publishesModuleDescriptorDiagnostics() {
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(
                    "file:///module.norm",
                    "norm",
                    1,
                    "Module module() { return module(name: \"sample\", version: 0, exports: []) }")));

    assertNotNull(client.diagnostics);
    assertEquals(
        "NORM-PROJECT-0001", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void assemblesAnUnsavedModuleFromOpenDocuments() throws Exception {
    Path root = temporaryDirectory.resolve("unsaved-project");
    Path entry = root.resolve("sample/Main.norm");
    Path library = root.resolve("sample/util/Identity.norm");
    Path module = root.resolve("sample/module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(library.getParent());
    String entryText = "package sample import sample.util.identity Void main() { identity(1) }";
    String libraryText =
        "package sample.util public Integer identity(Integer value) { return value }";
    String moduleText =
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }";
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(entry.toUri().toString(), "norm", 1, entryText)));
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(library.toUri().toString(), "norm", 1, libraryText)));
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(module.toUri().toString(), "norm", 1, moduleText)));

    List<? extends org.eclipse.lsp4j.Location> definitions =
        server
            .getTextDocumentService()
            .definition(
                new DefinitionParams(
                    new TextDocumentIdentifier(entry.toUri().toString()),
                    positionOfLast(entryText, "identity")))
            .get()
            .getLeft();

    assertEquals(1, definitions.size());
    assertEquals(library, Path.of(java.net.URI.create(definitions.getFirst().getUri())));
  }

  @Test
  void treatsPackageSourceNamedModuleNormAsAProjectDocument() throws Exception {
    Path root = temporaryDirectory.resolve("package-module-source");
    Path source = root.resolve("sample/internal/module.norm");
    Path library = root.resolve("sample/util/Identity.norm");
    Files.createDirectories(source.getParent());
    Files.createDirectories(library.getParent());
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }");
    Files.writeString(
        library, "package sample.util public Integer identity(Integer value) { return value }");
    String sourceText =
        "package sample.internal import sample.util.identity Integer value() { return identity(1) }";
    Files.writeString(source, sourceText);
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(source.toUri().toString(), "norm", 1, sourceText)));
    List<? extends org.eclipse.lsp4j.Location> definitions =
        server
            .getTextDocumentService()
            .definition(
                new DefinitionParams(
                    new TextDocumentIdentifier(source.toUri().toString()),
                    positionOfLast(sourceText, "identity")))
            .get()
            .getLeft();

    assertEquals(1, definitions.size());
    assertEquals(library, Path.of(java.net.URI.create(definitions.getFirst().getUri())));
  }

  @Test
  void exposesDefinitionReferencesAndRenameFromSemanticBindings() throws Exception {
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    String uri = "file:///navigation.norm";
    String text = "Void main() { Integer value = 1 printLine(value) }";
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

  @Test
  void navigatesAndRenamesAcrossProjectFiles() throws Exception {
    Path root = temporaryDirectory.resolve("project");
    Path library = root.resolve("sample/util/Identity.norm");
    Path entry = root.resolve("sample/Main.norm");
    Files.createDirectories(library.getParent());
    Files.createDirectories(entry.getParent());
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }");
    Files.writeString(
        library,
        "package sample.util\n\n"
            + "public class Box<T> {\n  T value\n}\n\n"
            + "private T preserve<T>(T value) {\n  return value\n}\n\n"
            + "public T identity<T>(T value) {\n  return preserve(value)\n}\n");
    String text =
        "package sample\n\n"
            + "import sample.util.Box\n"
            + "import sample.util.identity\n\n"
            + "Void main() {\n"
            + "  Box<List<Integer>> box = Box<List<Integer>>(value: List<Integer>())\n"
            + "  box.value.add(9)\n"
            + "  printLine(identity(value: box.value[0]))\n"
            + "}\n";
    Files.writeString(entry, text);
    String uri = entry.toUri().toString();
    if (File.separatorChar == '\\') {
      String path = entry.toUri().getPath();
      uri = "file:///" + Character.toLowerCase(path.charAt(1)) + "%3A" + path.substring(3);
    }
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem("untitled:project-peer", "norm", 1, "Void main() {}")));
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, text)));
    Position position = positionOfLast(text, "identity");
    TextDocumentIdentifier document = new TextDocumentIdentifier(uri);

    var definition =
        server
            .getTextDocumentService()
            .definition(new DefinitionParams(document, position))
            .get()
            .getLeft();
    var rename =
        server
            .getTextDocumentService()
            .rename(new RenameParams(document, position, "preserveValue"))
            .get();

    String libraryUri = definition.getFirst().getUri();
    String entryUri = uri;
    assertEquals(library, Path.of(java.net.URI.create(libraryUri)));
    assertEquals(2, rename.getChanges().get(uri).size());
    assertEquals(1, rename.getChanges().get(libraryUri).size());

    String libraryText = Files.readString(library);
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(libraryUri, "norm", 1, libraryText)));
    Position declaration = new Position(10, "public T ".length());
    var declarationReferences =
        server
            .getTextDocumentService()
            .references(
                new ReferenceParams(
                    new TextDocumentIdentifier(libraryUri),
                    declaration,
                    new ReferenceContext(true)))
            .get();
    var declarationRename =
        server
            .getTextDocumentService()
            .rename(
                new RenameParams(
                    new TextDocumentIdentifier(libraryUri), declaration, "mapIdentity"))
            .get();

    assertTrue(
        declarationReferences.stream().anyMatch(location -> location.getUri().equals(entryUri)));
    assertEquals(2, declarationRename.getChanges().get(uri).size());
  }

  @Test
  void closingAnUnsavedDependencyRestoresTheDiskProject() throws Exception {
    ProjectFixture fixture = projectFixture();
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, fixture.entryText())));
    String changedLibrary = fixture.libraryText().replace("identity<T>", "other<T>");
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.libraryUri(), "norm", 1, changedLibrary)));

    assertTrue(definition(server, fixture).isEmpty());

    server
        .getTextDocumentService()
        .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(fixture.libraryUri())));

    assertEquals(1, definition(server, fixture).size());
  }

  @Test
  void refreshesOpenProjectsWhenAnUnopenedDependencyChangesOnDisk() throws Exception {
    ProjectFixture fixture = projectFixture();
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, fixture.entryText())));
    assertEquals(1, definition(server, fixture).size());

    Files.writeString(fixture.library(), fixture.libraryText().replace("identity<T>", "other<T>"));
    server
        .getWorkspaceService()
        .didChangeWatchedFiles(
            new DidChangeWatchedFilesParams(
                List.of(new FileEvent(fixture.libraryUri(), FileChangeType.Changed))));

    assertTrue(definition(server, fixture).isEmpty());
  }

  @Test
  void reportsProjectLoadingFailuresInsteadOfFallingBackSilently() throws Exception {
    ProjectFixture fixture = projectFixture();
    Files.writeString(
        fixture.root().resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 0, exports: [\"util.Identity\"]) }");
    LanguageServer server = new LanguageServer();
    RecordingClient client = new RecordingClient();
    server.connect(client);

    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, fixture.entryText())));

    assertEquals(
        "NORM-PROJECT-0001", client.diagnostics.getDiagnostics().getFirst().getCode().getLeft());
  }

  @Test
  void completesExportedSymbolsWithImportEdits() throws Exception {
    ProjectFixture fixture = projectFixture();
    String text = "package sample\n\nVoid main() { iden }\n";
    Files.writeString(fixture.entry(), text);
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, text)));
    CompletionParams params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(fixture.entryUri()));
    params.setPosition(new Position(2, "Void main() { iden".length()));

    CompletionItem identity =
        server.getTextDocumentService().completion(params).get().getLeft().stream()
            .filter(item -> item.getLabel().equals("identity"))
            .findFirst()
            .orElseThrow();

    assertEquals(1, identity.getAdditionalTextEdits().size());
    assertEquals(
        "\n\nimport sample.util.identity",
        identity.getAdditionalTextEdits().getFirst().getNewText());
    var primaryEdit = identity.getTextEdit().getLeft();
    assertEquals("identity(${1:value})", primaryEdit.getNewText());
    assertEquals(new Position(2, "Void main() { ".length()), primaryEdit.getRange().getStart());
    assertEquals(new Position(2, "Void main() { iden".length()), primaryEdit.getRange().getEnd());
  }

  @Test
  void completesExpectedValuesInIncompleteProjectCalls() throws Exception {
    ProjectFixture fixture = projectFixture();
    String body =
        "Void consume(String value, Integer count) {} Void main() { "
            + "String label = \"ready\" Integer count = 1 consume(";
    String text = "package sample\n\n" + body;
    Files.writeString(fixture.entry(), text);
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, text)));
    CompletionParams params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(fixture.entryUri()));
    params.setPosition(new Position(2, body.length()));

    List<String> labels =
        server.getTextDocumentService().completion(params).get().getLeft().stream()
            .map(CompletionItem::getLabel)
            .toList();

    assertTrue(labels.containsAll(List.of("label", "count")), labels.toString());
    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void completesImportedGenericFieldMembersAfterAnIncompleteEdit() throws Exception {
    Path root = temporaryDirectory.resolve("generic-member-" + System.nanoTime());
    Path library = root.resolve("sample/util/Box.norm");
    Path entry = root.resolve("sample/Main.norm");
    Files.createDirectories(library.getParent());
    Files.createDirectories(entry.getParent());
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Box\"]) }");
    Files.writeString(library, "package sample.util public class Box<T> { T value }");
    String complete =
        "package sample import sample.util.Box Void main() { "
            + "Box<List<Integer>> box = Box<List<Integer>>(value: List<Integer>()) box.value.add(9) }";
    String incomplete = complete.replace("add(9)", "");
    Files.writeString(entry, complete);
    String uri = entry.toUri().toString();
    int offset = incomplete.indexOf("box.value.") + "box.value.".length();
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "norm", 1, complete)));
    server
        .getTextDocumentService()
        .didChange(
            new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                List.of(new TextDocumentContentChangeEvent(incomplete))));
    CompletionParams params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(uri));
    params.setPosition(new Position(0, offset));

    List<String> labels =
        server.getTextDocumentService().completion(params).get().getLeft().stream()
            .map(CompletionItem::getLabel)
            .toList();

    assertTrue(labels.containsAll(List.of("add", "size", "removeAt")), labels.toString());
  }

  @Test
  void renamesImportAliasesWithoutChangingTheirTargetDeclaration() throws Exception {
    ProjectFixture fixture = projectFixture();
    String entryText = fixture.entryText().replace("identity\n", "identity as localIdentity\n");
    entryText = entryText.replace("identity(1)", "localIdentity(1)");
    Files.writeString(fixture.entry(), entryText);
    LanguageServer server = new LanguageServer();
    server.connect(new RecordingClient());
    server
        .getTextDocumentService()
        .didOpen(
            new DidOpenTextDocumentParams(
                new TextDocumentItem(fixture.entryUri(), "norm", 1, entryText)));
    Position use = positionOfLast(entryText, "localIdentity");

    var definition =
        server
            .getTextDocumentService()
            .definition(new DefinitionParams(new TextDocumentIdentifier(fixture.entryUri()), use))
            .get()
            .getLeft();
    var rename =
        server
            .getTextDocumentService()
            .rename(
                new RenameParams(
                    new TextDocumentIdentifier(fixture.entryUri()), use, "mappedIdentity"))
            .get();

    assertEquals(fixture.library(), Path.of(java.net.URI.create(definition.getFirst().getUri())));
    assertEquals(2, rename.getChanges().get(fixture.entryUri()).size());
    assertTrue(!rename.getChanges().containsKey(fixture.libraryUri()));
  }

  private ProjectFixture projectFixture() throws Exception {
    Path root = temporaryDirectory.resolve("session-" + System.nanoTime());
    Path library = root.resolve("sample/util/Identity.norm");
    Path entry = root.resolve("sample/Main.norm");
    Files.createDirectories(library.getParent());
    Files.createDirectories(entry.getParent());
    Files.writeString(
        root.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }");
    String libraryText =
        "package sample.util\n\n" + "public T identity<T>(T value) {\n  return value\n}\n";
    String entryText =
        "package sample\n\n"
            + "import sample.util.identity\n\n"
            + "Void main() {\n  printLine(identity(1))\n}\n";
    Files.writeString(library, libraryText);
    Files.writeString(entry, entryText);
    return new ProjectFixture(
        root,
        library,
        entry,
        library.toUri().toString(),
        entry.toUri().toString(),
        libraryText,
        entryText);
  }

  private static List<? extends org.eclipse.lsp4j.Location> definition(
      LanguageServer server, ProjectFixture fixture) throws Exception {
    return server
        .getTextDocumentService()
        .definition(
            new DefinitionParams(
                new TextDocumentIdentifier(fixture.entryUri()),
                positionOfLast(fixture.entryText(), "identity")))
        .get()
        .getLeft();
  }

  private static Position positionOfLast(String source, String symbol) {
    int offset = source.lastIndexOf(symbol);
    if (offset < 0) {
      throw new IllegalArgumentException("symbol is absent from source: " + symbol);
    }
    int line = 0;
    int lineStart = 0;
    for (int index = 0; index < offset; index++) {
      if (source.charAt(index) == '\n') {
        line++;
        lineStart = index + 1;
      }
    }
    return new Position(line, offset - lineStart);
  }

  private record ProjectFixture(
      Path root,
      Path library,
      Path entry,
      String libraryUri,
      String entryUri,
      String libraryText,
      String entryText) {}

  private static final class RecordingClient implements LanguageClient {
    private PublishDiagnosticsParams diagnostics;
    private final Map<String, PublishDiagnosticsParams> diagnosticsByUri = new LinkedHashMap<>();

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      this.diagnostics = diagnostics;
      diagnosticsByUri.put(diagnostics.getUri(), diagnostics);
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
