package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.builtin.BuiltinSymbols;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

final class AnalysisTransactionTest {
  private final SourceFile source = SourceFile.of(Path.of("transaction.norm"), "Void main() {}");
  private final Syntax.Program program = SourceParser.parse(source).syntax();
  private final SourceSpan span = program.span();
  private final BuiltinSymbols builtins =
      new BuiltinSymbols(Set.of(source.id()), Set.of(), Set.of());
  private final SemanticModelBuilder model = new SemanticModelBuilder(builtins);
  private final BodyAnalysisState body = new BodyAnalysisState();
  private final TypeResolutionState resolution = new TypeResolutionState();
  private final DiagnosticBag diagnostics = new DiagnosticBag();
  private final AnalysisTransaction transactions =
      new AnalysisTransaction(model, body, resolution, diagnostics);

  @Test
  void nestedProbesRestoreEveryObservableFact() {
    model.putType(span, SemanticType.INTEGER);
    resolution.declareBound("T", SemanticType.INTEGER);
    int next = model.nextSymbolId();
    SymbolId local = SymbolId.source(source.id(), 200);
    try (var outer = transactions.probe()) {
      model.allocate(source.id());
      model.putType(span, SemanticType.STRING);
      model.putResultBuilder(span, SemanticType.STRING);
      body.recordAssignment(local);
      body.recordCapture(local);
      body.markCaptureReported(local);
      body.recordReferenceLifetime(span, LexicalLifetime.longLived());
      resolution.declareBound("T", SemanticType.STRING);
      diagnostics.error(SemanticDiagnosticCodes.TYPE_MISMATCH, "outer", span);
      assertTrue(outer.hasErrors());
      try (var inner = transactions.probe()) {
        model.putType(span, SemanticType.BOOLEAN);
        resolution.declareBound("T", SemanticType.BOOLEAN);
        diagnostics.error(SemanticDiagnosticCodes.TYPE_MISMATCH, "inner", span);
        assertTrue(inner.hasErrors());
      }
      assertEquals(SemanticType.STRING, model.semanticTypes().get(span));
      assertEquals(SemanticType.STRING, resolution.upperBound("T"));
      assertEquals(1, diagnostics.size());
      assertTrue(body.assigned(local));
    }
    assertEquals(next, model.nextSymbolId());
    assertEquals(SemanticType.INTEGER, model.semanticTypes().get(span));
    assertEquals(SemanticType.INTEGER, resolution.upperBound("T"));
    assertFalse(body.assigned(local));
    assertFalse(body.captured(local));
    assertTrue(body.markCaptureReported(local));
    assertNull(body.referenceLifetime(span));
    assertTrue(diagnostics.isEmpty());
    var frozen =
        model.build(
            program,
            CompilationScope.anonymous(List.of(source)),
            builtins,
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of());
    assertTrue(frozen.resultBuilder(span).isEmpty());
  }

  @Test
  void cancellationRestoresProbeAndResolutionContexts() {
    var failure = new CancellationException("cancelled probe");
    var actual =
        assertThrows(
            CancellationException.class,
            () -> {
              try (var probe = transactions.probe();
                  var scope =
                      resolution.enter(program, Map.of("T", SemanticType.STRING), Map.of())) {
                assertFalse(probe.hasErrors());
                model.putType(span, SemanticType.STRING);
                resolution.declareBound("T", SemanticType.STRING);
                throw failure;
              }
            });
    assertSame(failure, actual);
    assertTrue(model.semanticTypes().isEmpty());
    assertNull(resolution.program());
    assertTrue(resolution.parameters().isEmpty());
    assertNull(resolution.upperBound("T"));
  }

  @Test
  void rejectsOutOfOrderProbeClosureWithoutLosingEitherSnapshot() {
    var outer = transactions.probe();
    model.putType(span, SemanticType.STRING);
    var inner = transactions.probe();
    model.putType(span, SemanticType.INTEGER);
    assertThrows(IllegalStateException.class, outer::close);
    assertEquals(SemanticType.INTEGER, model.semanticTypes().get(span));
    inner.close();
    assertEquals(SemanticType.STRING, model.semanticTypes().get(span));
    outer.close();
    assertTrue(model.semanticTypes().isEmpty());
    outer.close();
  }

  @Test
  void resolutionScopesRestoreNestedContextsAndExposeReadOnlyMaps() {
    try (var outer = resolution.enter(program, Map.of("T", SemanticType.STRING), Map.of())) {
      assertThrows(
          UnsupportedOperationException.class,
          () -> resolution.parameters().put("U", SemanticType.BOOLEAN));
      try (var inner = resolution.enter(program, Map.of("U", SemanticType.BOOLEAN), Map.of())) {
        assertEquals(Map.of("U", SemanticType.BOOLEAN), resolution.parameters());
        assertThrows(IllegalStateException.class, outer::close);
      }
      assertEquals(Map.of("T", SemanticType.STRING), resolution.parameters());
    }
    assertNull(resolution.program());
    assertTrue(resolution.parameters().isEmpty());
  }
}
