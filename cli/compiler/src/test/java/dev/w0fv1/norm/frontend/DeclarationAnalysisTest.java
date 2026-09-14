package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DeclarationAnalysisTest {
  @Test
  void resolvesGenericDeclarationsBeforeCheckingImplementations() {
    var analyzer =
        analyzer(
            """
        interface Value {}
        class Empty implements Value {}
        T? value<T extends Value = Empty>(T? input = null) { missing() }
        """);
    var declared = analyzer.declarations();
    assertTrue(declared.diagnostics().isEmpty(), declared.diagnostics().toString());
    var value =
        declared.symbols().values().stream()
            .filter(symbol -> symbol.name().equals("value") && symbol.kind() == SymbolKind.FUNCTION)
            .findFirst()
            .orElseThrow();
    assertEquals(1, value.typeParameters().size());
    assertEquals("Value", value.typeParameters().getFirst().upperBound().orElseThrow().name());
    assertEquals("Empty", value.typeParameters().getFirst().defaultType().orElseThrow().name());
    assertTrue(value.parameters().getFirst().policy().hasDefault());
    assertEquals(value.type(), value.parameters().getFirst().type());
    assertFalse(
        declared.symbols().values().stream().anyMatch(symbol -> symbol.name().equals("missing")));
    var analyzed = analyzer.analyze(false, CompilationRequest.Kind.LIBRARY, Map.of());
    assertTrue(analyzed.analysis().hasErrors());
    assertTrue(
        analyzed.analysis().diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("missing")));
  }

  @Test
  void reportsDeclarationErrorsBeforeCheckingBodies() {
    var analyzer = analyzer("Integer value() { 1 } Integer value() { missing() }");
    var declared = analyzer.declarations();
    assertTrue(
        declared.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("already declared")));
    assertFalse(
        declared.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("missing")));
  }

  @Test
  void roundTripsTypedDeclarationFactsWithoutBodyFacts() throws Exception {
    var declared =
        analyzer("Integer value(Integer input = 42) { Integer local = input; local }")
            .declarations();
    var restored =
        dev.w0fv1.norm.core.store.PortableObjectCodec.decode(
            dev.w0fv1.norm.core.store.PortableObjectCodec.encode(declared),
            DeclarationAnalysis.class);
    assertEquals(declared, restored);
  }

  @Test
  void keepsDeclarationFactsImmutableWhenBodiesIntroduceLocals() {
    var analyzer = analyzer("Integer value(Integer input) { Integer local = input; local }");
    var declared = analyzer.declarations();
    int count = declared.symbols().size();
    var analyzed = analyzer.analyze(false, CompilationRequest.Kind.LIBRARY, Map.of());
    assertFalse(analyzed.analysis().hasErrors(), analyzed.analysis().diagnostics().toString());
    assertEquals(count, declared.symbols().size());
    assertFalse(
        declared.symbols().values().stream()
            .anyMatch(symbol -> symbol.kind() == SymbolKind.LOCAL_VARIABLE));
    assertTrue(
        analyzed.analysis().semanticModel().symbols().stream()
            .anyMatch(
                symbol ->
                    symbol.name().equals("local") && symbol.type().equals(SemanticType.INTEGER)));
    assertThrows(UnsupportedOperationException.class, () -> declared.symbols().clear());
  }

  private static Analyzer analyzer(String text) {
    var source = SourceFile.of(Path.of("declarations.norm"), text);
    var request = CompilationRequest.single(source).asLibrary();
    var program = SourceParser.parse(source).syntax();
    var programs = List.of(program);
    return new Analyzer(
        new SemanticAnalysisInput(
            programs,
            program,
            false,
            Set.of(),
            0,
            Set.of(),
            Set.of(),
            Set.of(),
            request.scope(),
            new DeclarationCatalog(programs, Set.of(), request.scope())),
        new DiagnosticBag(),
        CompilationControl.standard().begin());
  }
}
