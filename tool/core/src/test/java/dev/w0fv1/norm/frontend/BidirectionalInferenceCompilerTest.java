package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BidirectionalInferenceCompilerTest {
  @Test
  void contextuallyConstructsArrayAndListLiterals() {
    CompilationResult result =
        compile(
            "Void main() { Array<Integer> array = [1, 2, 3] "
                + "List<Integer> list = [4, 5, 6] "
                + "printLine(array[0]) printLine(list[0]) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void projectsProtocolExpectedTypesIntoCollectionLiterals() {
    CompilationResult result =
        compileWithStandardProtocols(
            "import std.testing.expectedOutputLines " + "Void main() { expectedOutputLines([]) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsMixedNumericLeavesInNumberCollections() {
    CompilationResult result =
        compile(
            "T choose<T>(T first, T second) { return first } "
                + "Void main() { List<Number> values = [1, 2.5, 2147483648] "
                + "var inferred = [1, 2.5, 2147483648] "
                + "var selected = choose(first: 1, second: 2.5) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersStringableForHeterogeneousPrintableCollectionLiterals() {
    CompilationResult result =
        compileWithStandardProtocols(
            "import std.io.printLines "
                + "Void main() { var values = [1, true, \"Norm\"] printLines(values) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersNestedDiamondConstructorsFromTheirExpectedTypes() {
    CompilationResult result =
        compile(
            "Void main() { List<Pair<Integer, String>> values = List<>() "
                + "values.add(Pair<>(first: 7, second: \"seven\")) "
                + "printLine(values[0].second) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersSourceClassDiamondsFromFieldsAndExpectedTypes() {
    CompilationResult result =
        compile(
            "class Cell<T> { T value } "
                + "Void main() { Cell<String> first = Cell<>(value: \"Norm\") "
                + "var second = Cell<Integer>(value: 7) "
                + "printLine(first.value) printLine(second.value) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsDiamondInDeclaredTypes() {
    CompilationResult result = compile("Void main() { List<> values = List<Integer>() }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsUnconstrainedDiamondAndInferredLocals() {
    CompilationResult diamond = compile("Void main() { List<>() }");
    CompilationResult nullValue = compile("Void main() { var value = null }");
    CompilationResult emptyValues = compile("Void main() { var values = [] }");

    assertFalse(diamond.isSuccess());
    assertFalse(nullValue.isSuccess());
    assertFalse(emptyValues.isSuccess());
  }

  @Test
  void propagatesExpectedTypesThroughGenericArguments() {
    CompilationResult result =
        compile(
            "class Cell<T> { T value } "
                + "Void accept<T>(Cell<T> value) {} "
                + "Void main() { accept<Integer>(value: Cell<>(value: 7)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void usesExpectedResultsToResolveContextualLiteralOverloads() {
    CompilationResult result =
        compile(
            "Array<String> choose(Array<String> values) { return values } "
                + "List<String> choose(List<String> values) { return values } "
                + "Void main() { Array<String> values = choose([\"Norm\"]) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void propagatesExpectedGenericResultsIntoNullArguments() {
    CompilationResult result =
        compile("Void main() { List<String?> values = List.filled(size: 2, value: null) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }

  private static CompilationResult compileWithStandardProtocols(String text) {
    SourceFile entry = SourceFile.of(Path.of("test.norm"), text);
    SourceFile protocols =
        SourceFile.of(
            DocumentId.of("stdlib:/std/core/protocols.norm"),
            "package std.core "
                + "interface Iterator<T> { Boolean hasNext() T next() } "
                + "interface Iterable<T> { Iterator<T> iterator() } "
                + "interface Stringable { String toString() }");
    SourceFile output =
        SourceFile.of(
            DocumentId.of("stdlib:/std/io/output.norm"),
            "package std.io import std.core.Iterable import std.core.Stringable "
                + "Void printLines<T extends Stringable>(Iterable<T> values) {}");
    SourceFile testing =
        SourceFile.of(
            DocumentId.of("stdlib:/std/testing/output.norm"),
            "package std.testing import std.core.Iterable "
                + "Void expectedOutputLines(Iterable<String> lines) {}");
    CompilationPrelude prelude =
        new CompilationPrelude(
            List.of(protocols, output, testing),
            Set.of(protocols.id(), output.id(), testing.id()),
            CompilationScope.module(
                new ModuleCoordinate("std", 1),
                Map.of(
                    protocols.id(), "std/core/protocols.norm",
                    output.id(), "std/io/output.norm",
                    testing.id(), "std/testing/output.norm")));
    return new CompilerSession(LanguageProfile.withPrelude(prelude)).compile(entry);
  }
}
