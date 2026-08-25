package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.PutBatchResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.List;
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
            LanguageProfile.current(),
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
            LanguageProfile.current(),
            store,
            CompilerSessionCapacity.standard(),
            () -> {},
            () -> {});
    SourceFile source = SourceFile.of(DocumentId.of("untitled:authoring"), "Void main() {}");

    CompilationSnapshot snapshot = compiler.snapshot(CompilationRequest.single(source));

    assertTrue(snapshot.document(source.id()).isPresent());
    assertEquals(0, storeAccesses.get());
  }
}
