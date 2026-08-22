package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static dev.w0fv1.norm.testing.NormTestKit.suite;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class StandardLibraryTest {
  @Test
  void executesImportedIntegerMathImplementedByTheStandardLibrary() {
    assertOutput(
        "import std.math.abs import std.math.clamp import std.math.max import std.math.min "
            + "import std.math.sign void main() { "
            + "print(min(left: 8, right: 3)) print(max(left: 8, right: 3)) "
            + "print(abs(-9)) print(clamp(value: 14, minimum: 2, maximum: 11)) print(sign(0)) }",
        "3",
        "8",
        "9",
        "11",
        "0");
  }

  @Test
  void requiresExplicitImportsForStandardLibraryFunctions() {
    assertFalse(compile("void main() { print(min(left: 1, right: 2)) }").isSuccess());
  }

  @Test
  void exposesReusableTestingPredicates() {
    assertOutput(
        "import std.testing.equal import std.testing.notEqual "
            + "import std.testing.isTrue import std.testing.isFalse "
            + "void main() { print(equal(actual: 4, expected: 4)) "
            + "print(notEqual(actual: \"a\", expected: \"b\")) "
            + "print(isTrue(true)) print(isFalse(false)) }",
        "true",
        "true",
        "true",
        "true");
  }

  @TestFactory
  Stream<DynamicTest> runsStandardLibraryPrograms() throws Exception {
    return suite("stdlib");
  }
}
