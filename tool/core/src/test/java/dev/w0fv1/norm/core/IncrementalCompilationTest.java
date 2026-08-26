package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.InMemoryDefinitionStore;
import dev.w0fv1.norm.core.store.PutBatchResult;
import dev.w0fv1.norm.core.store.PutResult;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.CompilerSessionCapacity;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IncrementalCompilationTest {
  @Test
  void reusesUnchangedDefinitionsAndRekeysOnlyDependencyClosure() {
    CompilerSession compiler =
        new CompilerSession(
            LanguageProfile.kernel(),
            new InMemoryDefinitionStore(),
            CompilerSessionCapacity.standard());
    CompilationOutput first =
        compile(
            compiler,
            "Integer leaf() { return 1 } Integer stable() { return 9 } "
                + "Void main() { printLine(leaf()) }");
    CompilationOutput changed =
        compile(
            compiler,
            "Integer leaf() { return 2 } Integer stable() { return 9 } "
                + "Void main() { printLine(leaf()) }");

    DefinitionId firstLeaf = first.artifact().namespace().definition("", "leaf").orElseThrow();
    DefinitionId changedLeaf = changed.artifact().namespace().definition("", "leaf").orElseThrow();
    DefinitionId firstStable = first.artifact().namespace().definition("", "stable").orElseThrow();
    DefinitionId changedStable =
        changed.artifact().namespace().definition("", "stable").orElseThrow();
    DefinitionId firstMain = first.artifact().entryDefinition();
    DefinitionId changedMain = changed.artifact().entryDefinition();

    assertNotEquals(firstLeaf, changedLeaf);
    assertNotEquals(firstMain, changedMain);
    assertEquals(firstStable, changedStable);
    assertTrue(changed.state().dependencies().dependenciesOf(changedMain).contains(changedLeaf));
    assertTrue(
        changed.state().delta().added().containsAll(java.util.Set.of(changedLeaf, changedMain)));
    assertTrue(changed.state().delta().reused().contains(changedStable));
    assertTrue(
        changed.state().delta().detached().containsAll(java.util.Set.of(firstLeaf, firstMain)));
    assertTrue(changed.state().buildReport().reusedGroups() > 0);
    assertTrue(changed.state().analysisReport().analyzedDeclarations() > 0);
    assertTrue(changed.state().analysisReport().reusedDeclarations() > 0);
    assertTrue(
        changed.state().analysisReport().analyzedDeclarations()
            < changed.state().analysisReport().declarations());
  }

  @Test
  void invalidatesSemanticDependentsWhenADeclarationSignatureChanges() {
    CompilerSession compiler = new CompilerSession();
    CompilationResult first =
        compiler.compile(
            SourceFile.of(
                Path.of("dependency.norm"),
                "Integer leaf(Integer value) { return value } Void main() { leaf(1) }"));
    SourceFile changedSource =
        SourceFile.of(
            Path.of("dependency.norm"),
            "Integer leaf(String  value) { return 1     } Void main() { leaf(1) }");
    var changedSnapshot = compiler.snapshot(changedSource);
    assertTrue(changedSnapshot.analysis().hasErrors());
    CompilationResult changed = compiler.compile(changedSource);

    assertTrue(first.isSuccess());
    assertTrue(
        changed.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic.severity() == dev.w0fv1.norm.diagnostic.DiagnosticSeverity.ERROR));
  }

  @Test
  void startsDeltaHistoryAtTheCompilerSessionBoundary() {
    CompilationOutput first = compile(new CompilerSession(), "Void main() { printLine(1) }");
    CompilationOutput independent = compile(new CompilerSession(), "Void main() { printLine(2) }");

    assertEquals(
        first.artifact().program().definitions().size(), first.state().delta().added().size());
    assertEquals(
        independent.artifact().program().definitions().size(),
        independent.state().delta().added().size());
    assertTrue(first.state().delta().reused().isEmpty());
    assertTrue(independent.state().delta().reused().isEmpty());
    assertTrue(first.state().delta().detached().isEmpty());
    assertTrue(independent.state().delta().detached().isEmpty());
    assertEquals(0, first.state().buildReport().reusedGroups());
    assertEquals(0, independent.state().buildReport().reusedGroups());
  }

  @Test
  void retainsDeltaHistoryWhenTheModuleSourceSetChanges() {
    CompilerSession compiler = new CompilerSession();
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
    CompilationOutput first =
        compile(
            compiler,
            new CompilationRequest(unit, entry.id(), List.of(entry, library), java.util.Set.of()));
    CompilationOutput changed =
        compile(
            compiler,
            new CompilationRequest(
                unit, entry.id(), List.of(entry, library, added), java.util.Set.of()));
    DefinitionId firstMain = first.artifact().entryDefinition();
    DefinitionId firstValue = first.artifact().namespace().definition("app", "value").orElseThrow();
    DefinitionId extra = changed.artifact().namespace().definition("app", "extra").orElseThrow();

    assertTrue(
        changed.state().delta().reused().containsAll(java.util.Set.of(firstMain, firstValue)));
    assertTrue(changed.state().delta().added().contains(extra));
    assertTrue(changed.state().delta().detached().isEmpty());
  }

  @Test
  void reusesUnchangedGroupsWithoutInvokingTheStoreAgain() {
    RecordingDefinitionStore store = new RecordingDefinitionStore();
    CompilerSession compiler =
        new CompilerSession(LanguageProfile.kernel(), store, CompilerSessionCapacity.standard());

    compile(compiler, "Void main() { printLine(1) }");
    int initialWrites = store.writes();
    int initialBatches = store.batches();
    compile(compiler, "Void main() { printLine(1) }");

    assertTrue(initialWrites > 0);
    assertEquals(1, initialBatches);
    assertEquals(1, store.batches());
    assertEquals(initialWrites, store.writes());
    assertEquals(0, store.reads());
  }

  @Test
  void skipsFrontendAndCoreWorkForAnUnchangedCompilationRequest() {
    CompilerSession compiler =
        new CompilerSession(
            LanguageProfile.kernel(),
            new InMemoryDefinitionStore(),
            CompilerSessionCapacity.standard());
    CompilationRequest request =
        CompilationRequest.single(
            SourceFile.of(
                Path.of("unchanged.norm"),
                "Integer value() { return 1 } Void main() { printLine(value()) }"));

    CompilationOutput first = compile(compiler, request);
    CompilationOutput reused =
        compile(
            compiler,
            CompilationRequest.single(
                SourceFile.of(
                    Path.of("unchanged.norm"),
                    "Integer value() { return 1 } Void main() { printLine(value()) }")));

    assertEquals(0, reused.state().analysisReport().analyzedDeclarations());
    assertEquals(
        first.state().analysisReport().declarations(),
        reused.state().analysisReport().reusedDeclarations());
    assertEquals(0, reused.state().analysisReport().elapsedNanos());
    assertEquals(
        first.artifact().program().groups().stream().map(CoreDefinitionGroup::id).toList(),
        reused.artifact().program().groups().stream().map(CoreDefinitionGroup::id).toList());
  }

  @Test
  void reanalyzesOnlyTheChangedIndependentDeclarationInALargeUnit() {
    CompilerSession compiler = new CompilerSession();
    StringBuilder source = new StringBuilder();
    for (int index = 0; index < 64; index++) {
      source.append("Integer value").append(index).append("() { return 1 } ");
    }
    source.append("Void main() {}");
    compile(compiler, source.toString());

    int changedLiteral = source.indexOf("return 1", source.indexOf("value32")) + "return ".length();
    source.setCharAt(changedLiteral, '2');
    CompilationOutput changed = compile(compiler, source.toString());

    assertEquals(1, changed.state().analysisReport().analyzedDeclarations());
    assertEquals(
        changed.state().analysisReport().declarations() - 1,
        changed.state().analysisReport().reusedDeclarations());
  }

  @Test
  void sharesIncrementalHistoryBetweenAnalysisAndCompilation() {
    CompilerSession compiler = new CompilerSession();
    compiler.snapshot(
        SourceFile.of(
            Path.of("analysis.norm"),
            "Integer changed() { return 1 } Integer stable() { return 2 } Void main() {}"));

    CompilationOutput changed =
        compile(
            compiler,
            new CompilationRequest(
                SourceFile.of(
                        Path.of("analysis.norm"),
                        "Integer changed() { return 3 } Integer stable() { return 2 } Void main() {}")
                    .id(),
                List.of(
                    SourceFile.of(
                        Path.of("analysis.norm"),
                        "Integer changed() { return 3 } Integer stable() { return 2 } Void main() {}"))));

    assertEquals(1, changed.state().analysisReport().analyzedDeclarations());
    assertEquals(
        changed.state().analysisReport().declarations() - 1,
        changed.state().analysisReport().reusedDeclarations());
  }

  @Test
  void startsANewAnalysisHistoryWhenTheCompilationScopeChanges() {
    CompilerSession compiler = new CompilerSession();
    CompilationUnitId unit = CompilationUnitId.of("unit:scope");
    SourceFile source = SourceFile.of(Path.of("scope.norm"), "Void main() {}");
    CompilationRequest first =
        new CompilationRequest(
            unit,
            CompilationScope.module(
                new ModuleCoordinate("first", 1), java.util.Map.of(source.id(), "Main.norm")),
            source.id(),
            List.of(source),
            java.util.Set.of());
    CompilationRequest changed =
        new CompilationRequest(
            unit,
            CompilationScope.module(
                new ModuleCoordinate("second", 1), java.util.Map.of(source.id(), "Main.norm")),
            source.id(),
            List.of(source),
            java.util.Set.of());
    compile(compiler, first);

    CompilationOutput output = compile(compiler, changed);

    assertEquals(0, output.state().analysisReport().reusedDeclarations());
    assertEquals(
        output.state().analysisReport().declarations(),
        output.state().analysisReport().analyzedDeclarations());
  }

  @Test
  void reportsGroupsThatTheStoreDoesNotAdmit() {
    DefinitionStore store =
        new DefinitionStore() {
          @Override
          public PutBatchResult putAll(List<byte[]> canonicalGroups) {
            return new PutBatchResult(
                canonicalGroups.stream()
                    .map(
                        canonicalGroup ->
                            new PutResult(
                                DefinitionHasher.hashGroup(canonicalGroup),
                                PutResult.Status.NOT_ADMITTED))
                    .toList());
          }

          @Override
          public Optional<byte[]> get(DefinitionGroupId id) {
            throw new AssertionError("core persistence must use the atomic put result");
          }
        };
    CompilerSession compiler =
        new CompilerSession(LanguageProfile.kernel(), store, CompilerSessionCapacity.standard());

    CoreBuildReport report =
        compile(compiler, "Void main() { printLine(1) }").state().buildReport();

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
    CompilationOutput first = compile(CompilerSession.persistent(store), source);
    CompilationOutput reused = compile(CompilerSession.persistent(store), source);

    assertEquals(
        first.artifact().program().groups().stream().map(CoreDefinitionGroup::id).toList(),
        reused.artifact().program().groups().stream().map(CoreDefinitionGroup::id).toList());
    assertEquals(0, reused.state().buildReport().storedGroups());
    assertEquals(
        reused.state().buildReport().groups(), reused.state().buildReport().reusedGroups());
  }

  private static CompilationOutput compile(CompilerSession compiler, String text) {
    return compiler
        .compile(SourceFile.of(Path.of("incremental.norm"), text))
        .program()
        .orElseThrow()
        .compilation();
  }

  private static CompilationOutput compile(CompilerSession compiler, CompilationRequest request) {
    return compiler.compile(request).program().orElseThrow().compilation();
  }

  private static final class RecordingDefinitionStore implements DefinitionStore {
    private final InMemoryDefinitionStore delegate = new InMemoryDefinitionStore();
    private final AtomicInteger batches = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();

    @Override
    public PutBatchResult putAll(List<byte[]> canonicalGroups) throws IOException {
      batches.incrementAndGet();
      PutBatchResult result = delegate.putAll(canonicalGroups);
      result.results().stream()
          .filter(value -> value.status() == PutResult.Status.STORED)
          .forEach(ignored -> writes.incrementAndGet());
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

    int batches() {
      return batches.get();
    }

    int reads() {
      return reads.get();
    }
  }
}
