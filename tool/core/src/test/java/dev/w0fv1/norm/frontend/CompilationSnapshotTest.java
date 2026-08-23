package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CompilationSnapshotTest {
  @Test
  void parsesUnchangedDocumentsOnceAndProjectsOneAnalysisIntoEveryDocument() {
    AtomicInteger parses = new AtomicInteger();
    CompilationEnvironment environment = CompilationEnvironment.create(parses::incrementAndGet);
    Compiler compiler = new Compiler(environment);
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
}
