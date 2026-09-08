package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SemanticQueryTest {
  @Test
  void preservesOverloadsAndReportsPageCompleteness() {
    var source =
        SourceFile.of(
            DocumentId.of("untitled:overloads"),
            "Integer answer() { return 42 } Integer answer(Integer value) { return value }");
    try (var language = new LanguageService()) {
      var query = language.query(language.snapshot(CompilationRequest.single(source)));
      var first = query.search("answer", 0, 1);
      var second = query.search("answer", 1, 1);
      assertEquals(2, first.total());
      assertTrue(first.hasMore());
      assertFalse(second.hasMore());
      assertNotEquals(
          first.items().getFirst().symbol().id(), second.items().getFirst().symbol().id());
      assertThrows(IllegalArgumentException.class, () -> query.search("", 0, 0));
    }
  }

  @Test
  void returnsLocalContextAndExactReferences() {
    var source =
        SourceFile.of(
            DocumentId.of("untitled:context"),
            "Integer dependency() { return 40 } Integer answer() { return dependency() + 2 } Integer unrelated() { return 7 } Void main() { printLine(answer()) }");
    try (var language = new LanguageService()) {
      var query = language.query(language.snapshot(CompilationRequest.single(source)));
      var selected = query.search("answer", 0, 10).items().getFirst();
      var context =
          query.context(selected.symbol().id(), selected.revision().orElseThrow(), 0, 20, true);
      assertEquals(
          "Integer answer() { return dependency() + 2 }", context.source().orElseThrow().text());
      assertTrue(
          context.dependencies().items().stream()
              .anyMatch(item -> item.symbol().name().equals("dependency")));
      assertFalse(
          context.dependencies().items().stream()
              .anyMatch(item -> item.symbol().name().equals("unrelated")));
      assertEquals(1, context.references().total());
      assertTrue(
          query
              .context(selected.symbol().id(), selected.revision().orElseThrow(), 0, 20, false)
              .source()
              .isEmpty());
    }
  }

  @Test
  void rejectsAStaleDocumentEvenWhenTheSymbolIdentitySurvives() {
    var id = DocumentId.of("untitled:revision");
    try (var language = new LanguageService()) {
      var before =
          language.query(
              language.snapshot(
                  CompilationRequest.single(SourceFile.of(id, "Integer answer() { return 42 }"))));
      var selected = before.search("answer", 0, 10).items().getFirst();
      var after =
          language.query(
              language.snapshot(
                  CompilationRequest.single(SourceFile.of(id, "Integer answer() { return 43 }"))));
      assertEquals(
          selected.symbol().id(), after.search("answer", 0, 10).items().getFirst().symbol().id());
      assertThrows(
          SemanticQuery.StaleRevisionException.class,
          () ->
              after.context(
                  selected.symbol().id(), selected.revision().orElseThrow(), 0, 10, true));
    }
  }

  @Test
  void findsCrossFileReferencesWhileRetainingDiagnostics() {
    var api = SourceFile.of(DocumentId.of("untitled:api"), "Integer answer() { return 42 }");
    var caller =
        SourceFile.of(
            DocumentId.of("untitled:caller"), "Void main() { printLine(answer()) missing() }");
    try (var language = new LanguageService()) {
      var query =
          language.query(language.snapshot(new CompilationRequest(api.id(), List.of(api, caller))));
      var selected = query.search("answer", 0, 10).items().getFirst();
      assertFalse(query.diagnostics().isEmpty());
      var context =
          query.context(selected.symbol().id(), selected.revision().orElseThrow(), 0, 10, true);
      assertEquals(caller.id(), context.references().items().getFirst().document());
      assertEquals(2, query.documents().size());
    }
  }
}
