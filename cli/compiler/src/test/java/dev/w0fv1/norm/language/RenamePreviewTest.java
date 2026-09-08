package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RenamePreviewTest {
  @Test
  void namedArgumentReferencesSurviveIncrementalRebasingAcrossFiles() {
    try (var language = new LanguageService()) {
      for (String prefix : List.of("", "\n\n")) {
        var api =
            SourceFile.of(
                DocumentId.of("untitled:api"), "Integer amount(Integer value) { return value }");
        var caller =
            SourceFile.of(
                DocumentId.of("untitled:caller"),
                prefix + "Integer use() { return amount(value: 2) }");
        var request = new CompilationRequest(api.id(), List.of(api, caller));
        var snapshot = language.snapshot(request);
        assertTrue(snapshot.diagnostics().isEmpty());
        var selected =
            language
                .query(snapshot)
                .select("amount.value", java.util.Optional.empty(), 0, 1)
                .items()
                .getFirst();
        var label =
            language.definition(snapshot.analysis(caller.id()), caller.text().indexOf("value:"));
        assertEquals(selected.symbol().declaration(), label);
        var preview =
            language.previewRename(
                request, selected.symbol().id(), selected.revision().orElseThrow(), "quantity");
        assertTrue(preview.after().isEmpty(), preview.after().toString());
        assertEquals(3, preview.changes().stream().mapToInt(change -> change.edits().size()).sum());
      }
    }
  }

  @Test
  void preservesDeclarationOperatorsAcrossIncrementalAnalysis() {
    var id = DocumentId.of("untitled:operators");
    String text =
        "value Box { Integer size } Void main() { printLine(Box.class) printLine(Box.size.field) }";
    try (var language = new LanguageService()) {
      for (String prefix : List.of("", "\n\n")) {
        var request = CompilationRequest.single(SourceFile.of(id, prefix + text));
        var snapshot = language.snapshot(request);
        assertTrue(snapshot.diagnostics().isEmpty(), snapshot.diagnostics().toString());
        var selected = language.query(snapshot).search("size", 0, 10).items().getFirst();
        var preview =
            language.previewRename(
                request, selected.symbol().id(), selected.revision().orElseThrow(), "length");
        assertTrue(preview.after().isEmpty(), preview.after().toString());
        assertEquals(2, preview.changes().getFirst().edits().size());
        assertTrue(
            preview.changes().getFirst().edits().stream()
                .allMatch(edit -> edit.oldText().equals("size")));
        assertTrue(
            language
                .prepareRename(snapshot.analysis(), prefix.length() + text.indexOf(".field") + 1)
                .isEmpty());
        assertTrue(
            language
                .prepareRename(snapshot.analysis(), prefix.length() + text.indexOf(".class") + 1)
                .isEmpty());
        assertTrue(
            language
                .definition(snapshot.analysis(), prefix.length() + text.indexOf(".field") + 1)
                .isPresent());
      }
    }
  }

  @Test
  void validatesCapturedTextWithoutRereadingOrOverwritingDisk(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var path = directory.resolve("captured.norm");
    java.nio.file.Files.writeString(path, "Integer answer() { return 42 }");
    var request = CompilationRequest.single(SourceFile.read(path));
    try (var language = new LanguageService()) {
      var selected =
          language.query(language.snapshot(request)).search("answer", 0, 10).items().getFirst();
      java.nio.file.Files.writeString(path, "Integer answer() { return missing }");
      var preview =
          language.previewRename(
              request, selected.symbol().id(), selected.revision().orElseThrow(), "result");
      assertTrue(preview.after().isEmpty());
      assertEquals("Integer answer() { return missing }", java.nio.file.Files.readString(path));
      assertNotEquals(DocumentRevision.of(SourceFile.read(path)), preview.inputs().getFirst());
    }
  }

  @Test
  void previewsCrossFileEditsAndValidatesCapturedInputs() {
    var api = SourceFile.of(DocumentId.of("untitled:api"), "Integer answer() { return 42 }");
    var caller =
        SourceFile.of(DocumentId.of("untitled:caller"), "Void main() { printLine(answer()) }");
    var request = new CompilationRequest(api.id(), List.of(api, caller));
    try (var language = new LanguageService()) {
      var query = language.query(language.snapshot(request));
      var selected = query.search("answer", 0, 10).items().getFirst();
      var preview =
          language.previewRename(
              request, selected.symbol().id(), selected.revision().orElseThrow(), "result");
      assertTrue(preview.before().isEmpty());
      assertTrue(preview.after().isEmpty(), preview.after().toString());
      assertEquals(2, preview.changes().size());
      assertEquals(2, preview.inputs().size());
      assertEquals("Integer answer() { return 42 }", api.text());
      assertTrue(
          preview.changes().stream()
              .allMatch(
                  change ->
                      change.edits().stream()
                          .allMatch(
                              edit ->
                                  edit.oldText().equals("answer")
                                      && edit.newText().equals("result"))));
    }
  }

  @Test
  void retainsExistingErrorsAndRejectsStaleOrConflictingTargets() {
    var source =
        SourceFile.of(
            DocumentId.of("untitled:broken"),
            "Integer answer() { return missing } Integer existing() { return 0 }");
    var request = CompilationRequest.single(source);
    try (var language = new LanguageService()) {
      var selected =
          language.query(language.snapshot(request)).search("answer", 0, 10).items().getFirst();
      var preview =
          language.previewRename(
              request, selected.symbol().id(), selected.revision().orElseThrow(), "result");
      assertFalse(preview.before().isEmpty());
      assertEquals(preview.before().getFirst().code(), preview.after().getFirst().code());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              language.previewRename(
                  request, selected.symbol().id(), selected.revision().orElseThrow(), "existing"));
      var changed = CompilationRequest.single(SourceFile.of(source.id(), source.text() + " "));
      assertThrows(
          SemanticQuery.StaleRevisionException.class,
          () ->
              language.previewRename(
                  changed, selected.symbol().id(), selected.revision().orElseThrow(), "result"));
    }
  }
}
