package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CompilerTest {
  @Test
  void requiresCompletePascalCaseBuiltinTypeNames() {
    CompilationResult complete =
        compile(
            "Integer identity(Integer value) { return value } "
                + "Void main() { Boolean matches = identity(1) == 1 printLine(matches) }");
    CompilationResult abbreviated =
        compile("int identity(int value) { return value } void main() { printLine(identity(1)) }");

    assertTrue(complete.isSuccess(), () -> complete.diagnostics().toString());
    assertFalse(abbreviated.isSuccess());
    assertTrue(
        abbreviated.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("unknown type 'int'")),
        () -> abbreviated.diagnostics().toString());
  }

  @Test
  void rejectsNonVoidFunctionsThatCanFallThrough() {
    CompilationResult result =
        compile("Integer choose(Boolean condition) { if condition { return 1 } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("must return Integer")));
  }

  @Test
  void enforcesPrivateClassMemberAccess() {
    CompilationResult internal =
        compile(
            "class Secret { private Integer value private Integer reveal() { return value } "
                + "Integer expose() { return this.reveal() } } "
                + "Void main() { Secret secret = Secret(value: 7) printLine(secret.expose()) }");
    CompilationResult field =
        compile(
            "class Secret { private Integer value } "
                + "Void main() { Secret secret = Secret(value: 7) printLine(secret.value) }");
    CompilationResult method =
        compile(
            "class Secret { private Integer reveal() { return 7 } } "
                + "Void main() { Secret secret = Secret() printLine(secret.reveal()) }");

    assertTrue(internal.isSuccess(), () -> internal.diagnostics().toString());
    assertFalse(field.isSuccess());
    assertFalse(method.isSuccess());
  }

  @Test
  void analyzesAModuleDescriptorAsACompileTimeObject() {
    AnalysisResult result =
        new Compiler()
            .analyze(
                SourceFile.of(
                    Path.of("module.norm"),
                    "Module(name: \"sample\", version: 1, exports: [\"math.integer\"])"));

    assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
  }

  @Test
  void reportsInvalidModuleDescriptors() {
    AnalysisResult result =
        new Compiler()
            .analyze(
                SourceFile.of(
                    Path.of("module.norm"), "Module(name: \"sample\", version: 0, exports: [])"));

    assertTrue(result.hasErrors());
    assertEquals("NORM-MODULE-0001", result.diagnostics().getFirst().code().value());
  }

  @Test
  void producesCheckedSyntaxForHelloWorld() {
    CompilationResult result = compile("Void main() { printLine(\"Hello from Norm\") }");

    assertTrue(result.isSuccess());
    assertTrue(result.diagnostics().isEmpty());
    var statements =
        result.program().orElseThrow().boundProgram().entryCallable().body().statements();
    assertEquals(1, statements.size());
    var statement = (dev.w0fv1.norm.bound.BoundStatement.ExpressionStatement) statements.getFirst();
    var printLine = (dev.w0fv1.norm.bound.BoundIntrinsic) statement.expression();
    var value =
        (dev.w0fv1.norm.bound.BoundExpression.Literal) printLine.arguments().getFirst().value();
    assertEquals("Hello from Norm", value.value());
  }

  @Test
  void reportsAMissingEntryPoint() {
    CompilationResult result = compile("Void helper() {}");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsUnknownFunctionsBeforeExecution() {
    CompilationResult result = compile("Void main() { unknown(\"value\") }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0003", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsAnInvalidPrintSignature() {
    CompilationResult result = compile("Void main() { printLine() }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-TYPE-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void checksEnumValuesAsTheirDeclaredType() {
    CompilationResult result =
        compile(
            "enum Color { Red, Green, Blue } "
                + "Color favorite() { return Color.Green } "
                + "Void main() { Color color = favorite() printLine(color == Color.Green) }");

    assertTrue(result.isSuccess());
  }

  @Test
  void rejectsUnknownEnumMembers() {
    CompilationResult result = compile("enum Color { Red } Void main() { printLine(Color.Blue) }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-NAME-0003", result.diagnostics().getFirst().code().value());
  }

  @Test
  void rejectsMethodsFromADifferentContainer() {
    CompilationResult result =
        compile("Void main() { Array<Integer> values = [1] values.push(2) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().value().equals("NORM-NAME-0003")),
        () -> result.diagnostics().toString());
  }

  @Test
  void acceptsColonNamedArgumentsInAnyOrder() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { printLine(subtract(right: 3, left: 10)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsEqualsAsAnArgumentLabel() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { printLine(subtract(right = 3, left = 10)) }");

    assertFalse(result.isSuccess());
    assertEquals("NORM-PARSER-0001", result.diagnostics().getFirst().code().value());
  }

  @Test
  void acceptsMatchingIdentifierShorthandForMultipleParameters() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { Integer left = 10 Integer right = 3 printLine(subtract(left, right)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsUnlabelledExpressionsForMultipleParameters() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { printLine(subtract(10, 3)) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("must be named")));
  }

  @Test
  void rejectsIdentifierShorthandWhenTheParameterNameDiffers() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { Integer first = 10 Integer second = 3 printLine(subtract(first, second)) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("must be named")));
  }

  @Test
  void rejectsUnknownDuplicateAndMissingArgumentLabels() {
    CompilationResult result =
        compile(
            "Integer subtract(Integer left, Integer right) { return left - right } "
                + "Void main() { printLine(subtract(left: 10, left: 3)) }");

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
            "Void main() { Integer total = 0 "
                + "for value : range(start: 0, end: 4) { total = total + value } "
                + "printLine(total) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersLoopTypeFromGenericContainers() {
    CompilationResult result =
        compile(
            "Void main() { List<Integer> values = List<Integer>() for value : values { printLine(value) } }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void reservesCopyForClassIdentityCopying() {
    CompilationResult result =
        compile("class Box { Integer value Integer copy() { return value } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("method 'copy' is reserved")));
  }

  private static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
