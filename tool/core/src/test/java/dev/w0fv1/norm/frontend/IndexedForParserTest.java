package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class IndexedForParserTest {
  @Test
  void parsesInferredAndExplicitValueBindingsWithAnIndex() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    SourceFile source =
        SourceFile.of(
            Path.of("indexed-for.norm"),
            "Void main() { for value,index : [1] {} for Integer item,position : [2] {} }");
    Syntax.Program program =
        new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics).parse();
    Syntax.ForStatement inferred =
        (Syntax.ForStatement) program.functions().getFirst().body().getFirst();
    Syntax.ForStatement explicit =
        (Syntax.ForStatement) program.functions().getFirst().body().get(1);

    assertTrue(diagnostics.snapshot().isEmpty(), () -> diagnostics.snapshot().toString());
    assertTrue(inferred.variableType().isEmpty());
    assertEquals("index", inferred.index().orElseThrow().name());
    assertEquals("Integer", explicit.variableType().orElseThrow().name());
    assertEquals("position", explicit.index().orElseThrow().name());
  }
}
