package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CompilerTest {
  @Test
  void producesCheckedSyntaxForHelloWorld() {
    CompilationResult result = compile("void main() { print(\"Hello from Norm\") }");

    assertTrue(result.isSuccess());
    assertTrue(result.diagnostics().isEmpty());
    var statements = result.program().orElseThrow().entryPoint().body();
    assertEquals(1, statements.size());
    Syntax.ExpressionStatement statement = (Syntax.ExpressionStatement) statements.getFirst();
    Syntax.Call print = (Syntax.Call) statement.expression();
    Syntax.StringLiteralExpr value =
        (Syntax.StringLiteralExpr) print.arguments().getFirst().value();
    assertEquals("Hello from Norm", value.value());
  }

  @Test
  void reportsAMissingEntryPoint() {
    CompilationResult result = compile("void helper() {}");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsUnknownFunctionsBeforeExecution() {
    CompilationResult result = compile("void main() { unknown(\"value\") }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0003", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsAnInvalidPrintSignature() {
    CompilationResult result = compile("void main() { print() }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-TYPE-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void checksEnumValuesAsTheirDeclaredType() {
    CompilationResult result =
        compile(
            "enum Color { Red, Green, Blue } "
                + "Color favorite() { return Color.Green } "
                + "void main() { Color color = favorite() print(color == Color.Green) }");

    assertTrue(result.isSuccess());
  }

  @Test
  void rejectsUnknownEnumMembers() {
    CompilationResult result = compile("enum Color { Red } void main() { print(Color.Blue) }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0003", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsMethodsFromADifferentContainer() {
    CompilationResult result = compile("void main() { Array values = [1] values.push(2) }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0003", result.diagnostics().getFirst().code().value());
  }

  @Test
  void acceptsColonNamedArgumentsInAnyOrder() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { print(subtract(right: 3, left: 10)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsEqualsAsAnArgumentLabel() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { print(subtract(right = 3, left = 10)) }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-PARSER-0001", result.diagnostics().getFirst().code().value());
  }

  @Test
  void acceptsMatchingIdentifierShorthandForMultipleParameters() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { int left = 10 int right = 3 print(subtract(left, right)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsUnlabelledExpressionsForMultipleParameters() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { print(subtract(10, 3)) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("must be named")));
  }

  @Test
  void rejectsIdentifierShorthandWhenTheParameterNameDiffers() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { int first = 10 int second = 3 print(subtract(first, second)) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("must be named")));
  }

  @Test
  void rejectsUnknownDuplicateAndMissingArgumentLabels() {
    CompilationResult result =
        compile(
            "int subtract(int left, int right) { return left - right } "
                + "void main() { print(subtract(left: 10, left: 3)) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("supplied more than once")));
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("missing argument 'right'")));
  }

  @Test
  void infersRangeLoopBindingAsInt() {
    CompilationResult result =
        compile(
            "void main() { int total = 0 "
                + "for value : range(start: 0, end: 4) { total = total + value } "
                + "print(total) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void requiresAnExplicitLoopTypeForUngenericContainers() {
    CompilationResult result =
        compile("void main() { List values = List() for value : values { print(value) } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("cannot infer loop variable")));
  }

  @Test
  void reservesCopyForClassIdentityCopying() {
    CompilationResult result =
        compile("class Box { int value int copy() { return value } } void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("method 'copy' is reserved")));
  }

  private static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
