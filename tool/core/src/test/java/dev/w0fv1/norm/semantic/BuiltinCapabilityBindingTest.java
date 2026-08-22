package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BuiltinCapabilityBindingTest {
  @Test
  void recordsResolvedIterationAndIndexOperations() {
    var result =
        new Compiler()
            .analyze(
                SourceFile.of(
                    Path.of("capabilities.norm"),
                    """
                    void inspect(Map<String, int> values, List<int> items) {
                      for Pair<String, int> entry : values {
                        print(entry.first)
                      }
                      print(items[0])
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
    Syntax.Call print = (Syntax.Call) readStatement.expression();
    Syntax.Index read = (Syntax.Index) print.arguments().getFirst().value();
    Syntax.Assignment write =
        (Syntax.Assignment)
            function.body().stream()
                .filter(Syntax.Assignment.class::isInstance)
                .findFirst()
                .orElseThrow();

    assertEquals(
        IntrinsicId.MAP_ITERATOR,
        model.iterationOf(loop.iterable().span()).orElseThrow().intrinsic());
    assertEquals(
        IntrinsicId.LIST_INDEX_READ, model.indexOf(read.span()).orElseThrow().readIntrinsic());
    assertEquals(
        IntrinsicId.LIST_INDEX_WRITE,
        model.indexOf(write.target().span()).orElseThrow().writeIntrinsic().orElseThrow());
  }
}
