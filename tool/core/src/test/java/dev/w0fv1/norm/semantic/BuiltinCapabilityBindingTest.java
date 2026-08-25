package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BuiltinCapabilityBindingTest {
  @Test
  void recordsResolvedIterationAndIndexOperations() {
    var result =
        new CompilerSession()
            .analyze(
                SourceFile.of(
                    Path.of("capabilities.norm"),
                    """
                    Void inspect(Map<String, Integer> values, List<Integer> items) {
                      for Pair<String, Integer> entry : values {
                        printLine(entry.first)
                      }
                      printLine(items[0])
                      items[0] = 2
                    }
                    """));
    SemanticModel model = result.semanticModel();
    Syntax.FunctionDecl function =
        model.syntax().functions().stream()
            .filter(candidate -> candidate.name().equals("inspect"))
            .findFirst()
            .orElseThrow();
    Syntax.ForStatement loop =
        (Syntax.ForStatement)
            function.body().stream()
                .filter(Syntax.ForStatement.class::isInstance)
                .findFirst()
                .orElseThrow();
    Syntax.ExpressionStatement readStatement =
        (Syntax.ExpressionStatement)
            function.body().stream()
                .filter(Syntax.ExpressionStatement.class::isInstance)
                .findFirst()
                .orElseThrow();
    Syntax.Call printLine = (Syntax.Call) readStatement.expression();
    Syntax.Index read = (Syntax.Index) printLine.arguments().getFirst().value();
    Syntax.Assignment write =
        (Syntax.Assignment)
            function.body().stream()
                .filter(Syntax.Assignment.class::isInstance)
                .findFirst()
                .orElseThrow();

    ResolvedIteration.Strategy.Builtin iteration =
        assertInstanceOf(
            ResolvedIteration.Strategy.Builtin.class,
            model.iterationOf(loop.iterable().span()).orElseThrow().strategy());
    assertEquals(IntrinsicId.MAP_ITERATOR, iteration.intrinsic());
    assertEquals(
        IntrinsicId.LIST_INDEX_READ, model.indexOf(read.span()).orElseThrow().readIntrinsic());
    assertEquals(
        IntrinsicId.LIST_INDEX_WRITE,
        model.indexOf(write.target().span()).orElseThrow().writeIntrinsic().orElseThrow());
  }
}
