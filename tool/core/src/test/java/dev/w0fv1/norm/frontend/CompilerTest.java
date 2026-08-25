package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;
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
        new CompilerSession()
            .analyze(
                SourceFile.of(
                    Path.of("module.norm"),
                    "Module(name: \"sample\", version: 1, exports: [\"math.integer\"])"));

    assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
  }

  @Test
  void reportsInvalidModuleDescriptors() {
    AnalysisResult result =
        new CompilerSession()
            .analyze(
                SourceFile.of(
                    Path.of("module.norm"), "Module(name: \"sample\", version: 0, exports: [])"));

    assertTrue(result.hasErrors());
    assertEquals("NORM-MODULE-0001", result.diagnostics().getFirst().code().value());
  }

  @Test
  void analyzesAPackageSourceNamedModuleNormAsSourceCode() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("sample/internal/module.norm"),
                    "package sample.internal Void main() {}"));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void producesCheckedSyntaxForHelloWorld() {
    CompilationResult result = compile("Void main() { printLine(\"Hello from Norm\") }");

    assertTrue(result.isSuccess());
    assertTrue(result.diagnostics().isEmpty());
    var program = result.program().orElseThrow().compilation().artifact().program();
    var entry =
        (dev.w0fv1.norm.core.CoreDefinition.Callable)
            program
                .definition(
                    result.program().orElseThrow().compilation().artifact().entryDefinition())
                .orElseThrow();
    var statements = entry.body().statements();
    assertEquals(1, statements.size());
    var statement = (dev.w0fv1.norm.core.CoreStatement.ExpressionStatement) statements.getFirst();
    var printLine = (dev.w0fv1.norm.core.CoreExpression.Intrinsic) statement.expression();
    var value =
        (dev.w0fv1.norm.core.CoreExpression.Literal) printLine.arguments().getFirst().value();
    assertEquals("Hello from Norm", value.value());
  }

  @Test
  void givesCodePointLiteralsTheirOwnType() {
    CompilationResult result = compile("Void main() { CodePoint letter = '😀' printLine(letter) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsNullableDeclarationsReturnsAndNestedTypeArguments() {
    CompilationResult result =
        compile(
            "String? missing() { return null } "
                + "Void main() { String? text = missing() List<String?> values = List<>() "
                + "values.add(text) values.add(null) "
                + "List<String>? optionalValues = null printLine(values.size()) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNullAndNullableValuesAtNonNullTargets() {
    CompilationResult literal = compile("Void main() { String text = null }");
    CompilationResult value =
        compile("Void main() { String? optional = null String text = optional }");

    assertFalse(literal.isSuccess());
    assertFalse(value.isSuccess());
    assertTrue(
        literal.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().value().startsWith("NORM-NULL-")));
    assertTrue(
        value.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().value().startsWith("NORM-NULL-")));
  }

  @Test
  void rejectsNullableVoidAndUntypedNull() {
    CompilationResult nullableVoid = compile("Void? invalid() { return null } Void main() {}");
    CompilationResult untyped = compile("Void main() { null }");

    assertFalse(nullableVoid.isSuccess());
    assertFalse(untyped.isSuccess());
  }

  @Test
  void narrowsNullableLocalsAcrossConditionsAndEarlyReturn() {
    CompilationResult result =
        compile(
            "Void show(String? text) { "
                + "if text != null && text.codePointSize() > 0 { printLine(text) } "
                + "if text == null { return } printLine(text.codePointSize()) } "
                + "Void main() { show(text: null) show(text: \"Norm\") }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void narrowsTheRightSideOfNullGuardingDisjunctions() {
    CompilationResult result =
        compile(
            "Boolean empty(String? text) { return text == null || text.codePointSize() == 0 } "
                + "Void main() { printLine(empty(text: null)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNullableDereferenceAndInvalidatesNarrowingAfterAssignment() {
    CompilationResult direct =
        compile("Void main() { String? text = null printLine(text.codePointSize()) }");
    CompilationResult reassigned =
        compile(
            "Void show(String? text) { if text != null { text = null printLine(text.codePointSize()) } } "
                + "Void main() {}");

    assertFalse(direct.isSuccess());
    assertFalse(reassigned.isSuccess());
    assertTrue(
        direct.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().value().startsWith("NORM-NULL-")));
    assertTrue(
        reassigned.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().value().startsWith("NORM-NULL-")));
  }

  @Test
  void acceptsSafeAccessAndNullCoalescingWithPreciseResultTypes() {
    CompilationResult result =
        compile(
            "class User { String name } "
                + "Void main() { User? user = null String name = user?.name ?? \"guest\" "
                + "Integer size = user?.name?.codePointSize() ?? 0 printLine(name) printLine(size) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsBranchFlowSoundAcrossMutationAndFieldReads() {
    CompilationResult nestedMutation =
        compile(
            "Void show(String? text, Boolean clear) { if text != null { "
                + "if clear { text = null } printLine(text.codePointSize()) } } Void main() {}");
    CompilationResult mutableField =
        compile(
            "class Holder { String? text Void show() { "
                + "if text != null { printLine(text.codePointSize()) } } } Void main() {}");

    assertFalse(nestedMutation.isSuccess());
    assertFalse(mutableField.isSuccess());
  }

  @Test
  void supportsBothNullComparisonOrdersAndElseNarrowing() {
    CompilationResult result =
        compile(
            "Void show(String? text) { if null == text { printLine(\"missing\") } "
                + "else { printLine(text.codePointSize()) } } Void main() { show(text: null) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
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
  void rejectsUnknownEnumVariants() {
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
  void resolvesFunctionAndMethodOverloadsByArityAndType() {
    CompilationResult result =
        compile(
            "Integer choose(Integer value) { return value } "
                + "String choose(String value) { return value } "
                + "class Picker { Integer choose(Integer value) { return value } "
                + "String choose(String value) { return value } } "
                + "Void main() { Integer number = choose(value: 7) String text = choose(value: \"N\") "
                + "Picker picker = Picker() printLine(picker.choose(value: number)) "
                + "printLine(picker.choose(value: text)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void typesMapGetAsNullable() {
    CompilationResult result =
        compile(
            "Void main() { Map<String, Integer> values = Map<>() "
                + "Integer? missing = values.get(key: \"missing\") "
                + "Integer fallback = values.get(key: \"missing\") ?? 0 } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsDuplicateAndAmbiguousOverloads() {
    CompilationResult duplicate =
        compile(
            "Integer choose(Integer value) { return value } "
                + "Integer choose(Integer other) { return other } Void main() {}");
    CompilationResult ambiguous =
        compile(
            "Integer choose(Integer? value) { return 1 } "
                + "Integer choose(String? value) { return 2 } "
                + "Void main() { printLine(choose(value: null)) }");

    assertFalse(duplicate.isSuccess());
    assertFalse(ambiguous.isSuccess());
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
  void bindsAnOptionalIntegerIndexAfterTheLoopValue() {
    AnalysisResult analysis =
        new CompilerSession()
            .analyze(
                SourceFile.of(
                    Path.of("indexed-loop.norm"),
                    "Void main() { for value,index : [10, 20] { printLine(index) } }"));
    Syntax.ForStatement loop =
        (Syntax.ForStatement)
            analysis.semanticModel().syntax().functions().getFirst().body().getFirst();
    Syntax.ForIndex index = loop.index().orElseThrow();

    assertTrue(analysis.diagnostics().isEmpty(), () -> analysis.diagnostics().toString());
    assertEquals("value", loop.variableName());
    assertEquals("index", index.name());
    assertEquals(
        SemanticType.INTEGER,
        analysis.semanticModel().symbolOf(index.nameSpan()).orElseThrow().type());
  }

  @Test
  void acceptsConditionalForAndRequiresABooleanCondition() {
    CompilationResult accepted =
        compile(
            "Void main() { Integer value = 0 for value < 3 { value = value + 1 } printLine(value) }");
    CompilationResult rejected = compile("Void main() { for 1 {} }");

    assertTrue(accepted.isSuccess(), () -> accepted.diagnostics().toString());
    assertFalse(rejected.isSuccess());
  }

  @Test
  void acceptsTwoAndThreeArgumentRangeOverloads() {
    CompilationResult result =
        compile(
            "Void main() { for value : range(start: 0, end: 3) { printLine(value) } "
                + "for value : range(start: 5, end: 0, step: -2) { printLine(value) } }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void resolvesGenericTypeMembersAndSequenceMembers() {
    CompilationResult result =
        compile(
            "Void main() { List<Integer> values = List.filled(size: 3, value: 7) "
                + "Integer last = values.last() Integer removed = values.removeLast() "
                + "List<Integer> backwards = values.reversed() "
                + "Array<Boolean> flags = Array.filled(size: 2, value: false) "
                + "Array<Boolean> reversed = flags.reversed() printLine(last) printLine(removed) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void exposesAsciiDigitOperationsOnCodePoints() {
    CompilationResult result =
        compile(
            "Void main() { Boolean digit = '7'.isAsciiDigit() "
                + "Integer value = '7'.asciiDigitValue() printLine(digit) printLine(value) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersLoopTypeFromGenericContainers() {
    CompilationResult result =
        compile(
            "Void main() { List<Integer> values = List<>() for value : values { printLine(value) } }");

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
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
