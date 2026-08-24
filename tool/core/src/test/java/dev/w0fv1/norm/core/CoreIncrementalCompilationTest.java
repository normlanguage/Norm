package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.InMemoryDefinitionStore;
import dev.w0fv1.norm.core.store.PutResult;
import dev.w0fv1.norm.frontend.CompilationEnvironment;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoreIncrementalCompilationTest {
  @Test
  void reusesUnchangedDefinitionsAndRekeysOnlyDependencyClosure() {
    CompilationEnvironment environment =
        CompilationEnvironment.create(() -> {}, () -> {}, new InMemoryDefinitionStore());
    Compiler compiler = new Compiler(environment);
    CoreCompilation first =
        compile(
            compiler,
            "Integer leaf() { return 1 } Integer stable() { return 9 } "
                + "Void main() { printLine(leaf()) }");
    CoreCompilation changed =
        compile(
            compiler,
            "Integer leaf() { return 2 } Integer stable() { return 9 } "
                + "Void main() { printLine(leaf()) }");

    DefinitionId firstLeaf = first.namespace().definition("", "leaf").orElseThrow();
    DefinitionId changedLeaf = changed.namespace().definition("", "leaf").orElseThrow();
    DefinitionId firstStable = first.namespace().definition("", "stable").orElseThrow();
    DefinitionId changedStable = changed.namespace().definition("", "stable").orElseThrow();
    DefinitionId firstMain = first.entryDefinition();
    DefinitionId changedMain = changed.entryDefinition();

    assertNotEquals(firstLeaf, changedLeaf);
    assertNotEquals(firstMain, changedMain);
    assertEquals(firstStable, changedStable);
    assertTrue(changed.dependencies().dependenciesOf(changedMain).contains(changedLeaf));
    assertTrue(changed.delta().added().containsAll(java.util.Set.of(changedLeaf, changedMain)));
    assertTrue(changed.delta().reused().contains(changedStable));
    assertTrue(changed.delta().detached().containsAll(java.util.Set.of(firstLeaf, firstMain)));
    assertTrue(changed.buildReport().reusedGroups() > 0);
  }

  @Test
  void startsDeltaHistoryAtTheCompilerSessionBoundary() {
    CoreCompilation first = compile(new Compiler(), "Void main() { printLine(1) }");
    CoreCompilation independent = compile(new Compiler(), "Void main() { printLine(2) }");

    assertEquals(first.program().definitions().size(), first.delta().added().size());
    assertEquals(independent.program().definitions().size(), independent.delta().added().size());
    assertTrue(first.delta().reused().isEmpty());
    assertTrue(independent.delta().reused().isEmpty());
    assertTrue(first.delta().detached().isEmpty());
    assertTrue(independent.delta().detached().isEmpty());
    assertEquals(0, first.buildReport().reusedGroups());
    assertEquals(0, independent.buildReport().reusedGroups());
  }

  @Test
  void retainsDeltaHistoryWhenTheModuleSourceSetChanges() {
    Compiler compiler = new Compiler();
    CompilationUnitId unit = CompilationUnitId.of("file:///project/module.norm");
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///project/app/Main.norm"),
            "package app Void main() { printLine(value()) }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///project/app/Value.norm"),
            "package app Integer value() { return 1 }");
    SourceFile added =
        SourceFile.of(
            DocumentId.of("file:///project/app/Extra.norm"),
            "package app Integer extra() { return 2 }");
    CoreCompilation first =
        compile(
            compiler,
            new CompilationRequest(unit, entry.id(), List.of(entry, library), java.util.Set.of()));
    CoreCompilation changed =
        compile(
            compiler,
            new CompilationRequest(
                unit, entry.id(), List.of(entry, library, added), java.util.Set.of()));
    DefinitionId firstMain = first.entryDefinition();
    DefinitionId firstValue = first.namespace().definition("app", "value").orElseThrow();
    DefinitionId extra = changed.namespace().definition("app", "extra").orElseThrow();

    assertTrue(changed.delta().reused().containsAll(java.util.Set.of(firstMain, firstValue)));
    assertTrue(changed.delta().added().contains(extra));
    assertTrue(changed.delta().detached().isEmpty());
  }

  @Test
  void reusesUnchangedGroupsWithoutPublishingThemAgain() {
    RecordingDefinitionStore store = new RecordingDefinitionStore();
    Compiler compiler = new Compiler(CompilationEnvironment.create(() -> {}, () -> {}, store));

    compile(compiler, "Void main() { printLine(1) }");
    int initialWrites = store.writes();
    compile(compiler, "Void main() { printLine(1) }");

    assertTrue(initialWrites > 0);
    assertEquals(initialWrites, store.writes());
    assertEquals(0, store.reads());
  }

  @Test
  void reportsGroupsThatTheStoreDoesNotAdmit() {
    DefinitionStore store =
        new DefinitionStore() {
          @Override
          public PutResult put(byte[] canonicalGroup) {
            return new PutResult(
                DefinitionHasher.hashGroup(canonicalGroup), PutResult.Status.NOT_ADMITTED);
          }

          @Override
          public Optional<byte[]> get(DefinitionGroupId id) {
            throw new AssertionError("core persistence must use the atomic put result");
          }
        };
    Compiler compiler = new Compiler(CompilationEnvironment.create(() -> {}, () -> {}, store));

    CoreBuildReport report = compile(compiler, "Void main() { printLine(1) }").buildReport();

    assertTrue(report.groups() > 0);
    assertEquals(0, report.storedGroups());
    assertEquals(0, report.reusedGroups());
    assertEquals(report.groups(), report.notAdmittedGroups());
  }

  @Test
  void reusesPersistentDefinitionGroupsAcrossCompilerSessions(@TempDir Path directory)
      throws Exception {
    Path store = directory.resolve("definitions");
    String source = "Void main() { printLine(1) }";
    CoreCompilation first = compile(new Compiler(CompilationEnvironment.persistent(store)), source);
    CoreCompilation reused =
        compile(new Compiler(CompilationEnvironment.persistent(store)), source);

    assertEquals(
        first.program().groups().stream().map(CoreDefinitionGroup::id).toList(),
        reused.program().groups().stream().map(CoreDefinitionGroup::id).toList());
    assertEquals(0, reused.buildReport().storedGroups());
    assertEquals(reused.buildReport().groups(), reused.buildReport().reusedGroups());
  }

  private static CoreCompilation compile(Compiler compiler, String text) {
    return compiler
        .compile(SourceFile.of(Path.of("incremental.norm"), text))
        .program()
        .orElseThrow()
        .coreCompilation();
  }

  private static CoreCompilation compile(Compiler compiler, CompilationRequest request) {
    return compiler.compile(request).program().orElseThrow().coreCompilation();
  }

  private static final class RecordingDefinitionStore implements DefinitionStore {
    private final InMemoryDefinitionStore delegate = new InMemoryDefinitionStore();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();

    @Override
    public PutResult put(byte[] canonicalGroup) throws IOException {
      PutResult result = delegate.put(canonicalGroup);
      if (result.status() == PutResult.Status.STORED) writes.incrementAndGet();
      return result;
    }

    @Override
    public Optional<byte[]> get(DefinitionGroupId id) throws IOException {
      reads.incrementAndGet();
      return delegate.get(id);
    }

    int writes() {
      return writes.get();
    }

    int reads() {
      return reads.get();
    }
  }
}
