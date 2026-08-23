package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.w0fv1.norm.bound.BoundArgument;
import dev.w0fv1.norm.bound.BoundCall;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BoundIrArchitectureTest {
  @Test
  void exposesCompleteBoundExecutionModel() throws Exception {
    for (String type :
        new String[] {
          "BoundProgram",
          "BoundSource",
          "BoundClass",
          "BoundCallable",
          "BoundBlock",
          "BoundStatement",
          "BoundExpression",
          "BoundCall",
          "BoundArgument",
          "BoundConstruct",
          "BoundIntrinsic",
          "BoundRuntimeType",
          "BoundReifiedArgument",
          "BoundValueTransfer",
          "BoundDeclarationId"
        }) {
      assertNotNull(Class.forName("dev.w0fv1.norm.bound." + type));
    }
  }

  @Test
  void typedProgramContainsOnlyBoundProgram() throws Exception {
    var components = dev.w0fv1.norm.value.TypedProgram.class.getRecordComponents();

    assertEquals(1, components.length);
    assertEquals("dev.w0fv1.norm.bound.BoundProgram", components[0].getType().getName());
  }

  @Test
  void lowererHasNoSyntaxOrSemanticExecutionDependency() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/dev/w0fv1/norm/truffle/Lowerer.java").toAbsolutePath());

    assertFalse(source.contains("dev.w0fv1.norm.syntax"));
    assertFalse(source.contains("SemanticModel"));
    assertFalse(source.contains("semanticModel()"));
    assertFalse(source.contains("Map<SourceSpan"));
  }

  @Test
  void bindsCanonicalTargetsArgumentOrderAndFieldOrdinals() {
    var source =
        SourceFile.of(
            Path.of("bound.norm"),
            "class Box { Integer value Void set(Integer next) { value = next } } "
                + "Integer choose(Integer first, Integer second) { return first } "
                + "Void main() { Box box = Box(value: 1) box.set(next: choose(second: 2, first: 3)) }");

    var program = new Compiler().compile(source).program().orElseThrow().boundProgram();
    var box =
        program.classes().stream()
            .filter(value -> value.name().equals("Box"))
            .findFirst()
            .orElseThrow();
    var choose =
        program.callables().stream()
            .filter(value -> value.name().equals("choose"))
            .findFirst()
            .orElseThrow();
    var set =
        (BoundCall)
            ((BoundStatement.ExpressionStatement)
                    program.entryCallable().body().statements().get(1))
                .expression();
    var nested = (BoundCall) set.arguments().getFirst().value();

    assertEquals(0, box.fields().getFirst().ordinal());
    assertEquals(choose.id(), nested.target());
    assertEquals(
        java.util.List.of(1, 0),
        nested.arguments().stream().map(BoundArgument::parameterIndex).toList());
    assertFalse(set.receiver().isEmpty());
  }
}
