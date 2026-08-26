package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CompilationOutput;
import dev.w0fv1.norm.core.store.InMemoryDefinitionStore;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CompilerSessionLifecycleTest {
  @Test
  void evictsLeastRecentlyUsedParsedDocumentsAtTheConfiguredCapacity() {
    AtomicInteger parses = new AtomicInteger();
    CompilerSession session = session(new CompilerSessionCapacity(1, 4), parses);
    SourceFile first = SourceFile.of(DocumentId.of("untitled:first"), "Void main() {}");
    SourceFile second = SourceFile.of(DocumentId.of("untitled:second"), "Void main() {}");

    session.snapshot(first);
    session.snapshot(second);
    session.snapshot(first);

    assertEquals(3, parses.get());
  }

  @Test
  void evictsCompilationHistoryAtTheConfiguredUnitCapacity() {
    CompilerSession session = session(new CompilerSessionCapacity(4, 1), new AtomicInteger());
    CompilationRequest first = request("first", "Void main() { printLine(1) }");
    CompilationRequest second = request("second", "Void main() { printLine(2) }");

    session.compile(first);
    session.compile(second);
    CompilationOutput recompiled =
        session
            .compile(request("first", "Void main() { printLine(3) }"))
            .program()
            .orElseThrow()
            .compilation();

    assertTrue(recompiled.state().delta().detached().isEmpty());
    assertEquals(
        recompiled.artifact().program().definitions().size(),
        recompiled.state().delta().added().size());
  }

  @Test
  void invalidatingADocumentDropsItsParseAndCompilationHistory() {
    AtomicInteger parses = new AtomicInteger();
    CompilerSession session = session(new CompilerSessionCapacity(4, 4), parses);
    CompilationRequest request = request("main", "Void main() { printLine(1) }");
    session.compile(request);

    session.invalidate(request.entryDocument());
    CompilationOutput recompiled = session.compile(request).program().orElseThrow().compilation();

    assertEquals(2, parses.get());
    assertTrue(recompiled.state().delta().detached().isEmpty());
  }

  @Test
  void rejectsWorkAfterClose() {
    CompilerSession session = session(new CompilerSessionCapacity(4, 4), new AtomicInteger());
    session.close();

    assertThrows(
        IllegalStateException.class, () -> session.snapshot(source("closed", "Void main() {}")));
    assertThrows(
        IllegalStateException.class, () -> session.invalidate(DocumentId.of("untitled:closed")));
  }

  private static CompilerSession session(CompilerSessionCapacity capacity, AtomicInteger parses) {
    return new CompilerSession(
        LanguageProfile.kernel(),
        new InMemoryDefinitionStore(),
        capacity,
        parses::incrementAndGet,
        () -> {});
  }

  private static CompilationRequest request(String name, String text) {
    SourceFile source = source(name, text);
    return new CompilationRequest(
        CompilationUnitId.of("unit:" + name), source.id(), List.of(source), Set.of());
  }

  private static SourceFile source(String name, String text) {
    return SourceFile.of(DocumentId.of("untitled:" + name), text);
  }
}
