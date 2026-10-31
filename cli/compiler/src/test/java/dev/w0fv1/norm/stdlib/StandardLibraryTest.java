package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

final class StandardLibraryTest {
  @Test
  void constructsOrderedHttpHeadersFromNamesPairsAndLines() {
    assertOutput(
        """
        import std.http.HeaderMap
        import std.core.Exception
        Void main() {
          var one = HeaderMap(k1: "A", v1: "")
          var two = HeaderMap(k1: "A", v1: "", k2: "A", v2: "https://host:8080")
          var lines = HeaderMap(["A:", "A: https://host:8080"])
          var pairs = HeaderMap(["A", "", "A", "https://host:8080"])
          printLine(one.values.size())
          printLine(HeaderMap(["X-Trace:   id:2   "]).values[0].value)
          printLine(two == lines && lines == pairs)
          printLine(HeaderMap(entries: []).values.size())
          try { HeaderMap(["A", "B", "C"]) } catch Exception error { printLine(error.message) }
          try { HeaderMap(["A: B", "C"]) } catch Exception error { printLine(error.message) }
        }
        """,
        "1",
        "id:2",
        "true",
        "0",
        "Missing header value at index 3",
        "Missing colon in header at index 1");
  }

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

  @Test
  void runsStandardLibraryTestsThroughThePublicTestRunner() throws Exception {
    var environment =
        dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(
            new dev.w0fv1.norm.runtime.NormRuntime());
    try (var runner = dev.w0fv1.norm.application.ApplicationRunner.open(environment)) {
      var result =
          runner.test(
              Path.of(System.getProperty("norm.test.stdlib")),
              dev.w0fv1.norm.execution.ExecutionContext.of(
                  new java.io.PrintWriter(java.io.Writer.nullWriter()),
                  dev.w0fv1.norm.platform.jdk.JdkSystemPlatform.standard()));
      org.junit.jupiter.api.Assertions.assertTrue(
          result.compilation().isSuccess(), () -> result.compilation().diagnostics().toString());
      var report = result.report().orElseThrow();
      org.junit.jupiter.api.Assertions.assertTrue(report.testsFound() > 0);
      org.junit.jupiter.api.Assertions.assertTrue(
          report.isSuccess(), () -> report.failures().toString());
    }
  }
}
