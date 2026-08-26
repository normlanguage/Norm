package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static dev.w0fv1.norm.testing.NormTestKit.suite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class StandardLibraryTest {
  @Test
  void executesImportedIntegerMathImplementedByTheStandardLibrary() {
    assertOutput(
        "import std.math.abs import std.math.clamp import std.math.max import std.math.min "
            + "import std.math.sign Void main() { "
            + "printLine(min(left: 8, right: 3)) printLine(max(left: 8, right: 3)) "
            + "printLine(abs(-9)) printLine(clamp(value: 14, minimum: 2, maximum: 11)) printLine(sign(0)) }",
        "3",
        "8",
        "9",
        "11",
        "0");
  }

  @Test
  void rejectsAnInvertedClampInterval() throws Exception {
    Path source =
        Path.of(
            Objects.requireNonNull(
                    StandardLibraryTest.class.getResource("/runtime/invalid_clamp.norm"))
                .toURI());
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class, () -> dev.w0fv1.norm.testing.NormTestKit.run(source));

    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, exception.code());
  }

  @Test
  void requiresExplicitImportsForStandardLibraryFunctions() {
    assertFalse(compile("Void main() { printLine(min(left: 1, right: 2)) }").isSuccess());
  }

  @Test
  void exposesReusableTestingPredicates() {
    assertOutput(
        "import std.testing.equal import std.testing.notEqual "
            + "import std.testing.isTrue import std.testing.isFalse "
            + "Void main() { printLine(equal(actual: 4, expected: 4)) "
            + "printLine(notEqual(actual: \"a\", expected: \"b\")) "
            + "printLine(isTrue(true)) printLine(isFalse(false)) }",
        "true",
        "true",
        "true",
        "true");
  }

  @Test
  void rebuildsStringsFromCodePoints() {
    assertOutput(
        "import std.text.fromCodePoints Void main() { "
            + "printLine(fromCodePoints(values: ['N', 'o', 'r', 'm', '😀'])) }",
        "Norm😀");
  }

  @Test
  void normalizesUnicodeTextThroughTheStandardLibrary() {
    assertOutput(
        "import std.text.Normalization import std.text.isNormalized import std.text.normalize "
            + "Void main() { String value = normalize(value: \"é\", form: Normalization.Nfc) "
            + "printLine(value.codePointSize()) printLine(isNormalized(value: value, form: Normalization.Nfc)) }",
        "1",
        "true");
  }

  @TestFactory
  Stream<DynamicTest> runsStandardLibraryPrograms() throws Exception {
    return suite("stdlib");
  }
}
