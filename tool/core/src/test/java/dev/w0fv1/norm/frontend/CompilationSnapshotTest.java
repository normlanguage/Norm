package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.PutBatchResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CompilationSnapshotTest {
  @Test
  void parsesUnchangedDocumentsOnceAndProjectsOneAnalysisIntoEveryDocument() {
    AtomicInteger parses = new AtomicInteger();
    CompilerSession compiler =
        new CompilerSession(
            LanguageProfile.kernel(),
            new dev.w0fv1.norm.core.store.InMemoryDefinitionStore(),
            CompilerSessionCapacity.standard(),
            parses::incrementAndGet,
            () -> {});
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///project/library.norm"),
            "package app Integer twice(Integer value) { return value + value }");
    SourceFile main =
        SourceFile.of(
            DocumentId.of("file:///project/main.norm"),
            "package app Void main() { printLine(twice(2)) }");
    CompilationRequest request =
        new CompilationRequest(main.id(), List.of(library, main), Set.of());

    CompilationSnapshot first = compiler.snapshot(request);
    int afterFirst = parses.get();
    CompilationSnapshot second = compiler.snapshot(request);

    assertTrue(first.document(library.id()).isPresent());
    assertTrue(first.document(main.id()).isPresent());
    assertSame(first.semanticModel(), first.document(main.id()).orElseThrow().projectModel());
    assertEquals(afterFirst, parses.get());
    assertEquals(first.documentIds(), second.documentIds());
  }

  @Test
  void keepsAuthoringSnapshotsIndependentFromCoreMaterialization() {
    AtomicInteger storeAccesses = new AtomicInteger();
    DefinitionStore store =
        new DefinitionStore() {
          @Override
          public PutBatchResult putAll(List<byte[]> canonicalGroups) throws IOException {
            storeAccesses.incrementAndGet();
            throw new IOException("unexpected write");
          }

          @Override
          public Optional<byte[]> get(DefinitionGroupId id) throws IOException {
            storeAccesses.incrementAndGet();
            throw new IOException("unexpected read");
          }
        };
    CompilerSession compiler =
        new CompilerSession(
            LanguageProfile.kernel(),
            store,
            CompilerSessionCapacity.standard(),
            () -> {},
            () -> {});
    SourceFile source = SourceFile.of(DocumentId.of("untitled:authoring"), "Void main() {}");

    CompilationSnapshot snapshot = compiler.snapshot(CompilationRequest.single(source));

    assertTrue(snapshot.document(source.id()).isPresent());
    assertEquals(0, storeAccesses.get());
  }

  @Test
  void analyzesPreludeSourceOverlaysWithTheirCanonicalDocumentIdentity() {
    DocumentId document = DocumentId.of("stdlib:/std/example.norm");
    SourceFile original = SourceFile.of(document, "package std Integer answer() { return 42 }");
    CompilationPrelude prelude =
        new CompilationPrelude(
            List.of(original),
            Set.of(document),
            CompilationScope.module(
                new ModuleCoordinate("std", 1), Map.of(document, "std/example.norm")));
    CompilerSession compiler = new CompilerSession(LanguageProfile.withPrelude(prelude));
    SourceFile overlay = SourceFile.of(document, "package std Integer answer() { return missing }");

    CompilationSnapshot snapshot = compiler.preludeSnapshot(overlay);

    assertEquals(overlay.text(), snapshot.entryDocument().source().text());
    assertTrue(
        snapshot.diagnostics(document).stream()
            .anyMatch(diagnostic -> diagnostic.code().value().equals("NORM-NAME-0003")));
  }

  @Test
  void analyzesMultiplePreludeSourceOverlaysInOneSemanticSnapshot() {
    DocumentId firstDocument = DocumentId.of("stdlib:/std/first.norm");
    DocumentId secondDocument = DocumentId.of("stdlib:/std/second.norm");
    SourceFile first =
        SourceFile.of(firstDocument, "package std Integer first() { return 1 }");
    SourceFile second =
        SourceFile.of(secondDocument, "package std Integer second() { return 2 }");
    CompilationPrelude prelude =
        new CompilationPrelude(
            List.of(first, second),
            Set.of(firstDocument, secondDocument),
            CompilationScope.module(
                new ModuleCoordinate("std", 1),
                Map.of(firstDocument, "std/first.norm", secondDocument, "std/second.norm")));
    CompilerSession compiler = new CompilerSession(LanguageProfile.withPrelude(prelude));
    SourceFile firstOverlay =
        SourceFile.of(firstDocument, "package std Integer first() { return missingFirst }");
    SourceFile secondOverlay =
        SourceFile.of(secondDocument, "package std Integer second() { return missingSecond }");

    CompilationSnapshot snapshot =
        compiler.preludeSnapshot(List.of(firstOverlay, secondOverlay), secondDocument);

    assertEquals(secondOverlay.text(), snapshot.entryDocument().source().text());
    assertEquals(
        firstOverlay.text(), snapshot.document(firstDocument).orElseThrow().source().text());
    assertTrue(
        snapshot.diagnostics(firstDocument).stream()
            .anyMatch(diagnostic -> diagnostic.code().value().equals("NORM-NAME-0003")));
    assertTrue(
        snapshot.diagnostics(secondDocument).stream()
            .anyMatch(diagnostic -> diagnostic.code().value().equals("NORM-NAME-0003")));
  }
}
