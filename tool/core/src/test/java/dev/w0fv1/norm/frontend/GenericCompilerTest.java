package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GenericCompilerTest {
  @Test
  void acceptsGenericClassesAndNestedTypeArguments() {
    CompilationResult result =
        compile(
            "class Box<T> { T value } "
                + "Void main() { Box<List<Integer>> box = Box<List<Integer>>(value: List<Integer>()) "
                + "box.value.add(7) printLine(box.value[0]) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericFunctionArgumentsExactly() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "Void main() { String value = identity(value: \"Norm\") printLine(value) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericFunctionArgumentsFromTheExpectedType() {
    CompilationResult result =
        compile(
            "class Box<T> {} Box<T> empty<T>() { return Box<T>() } "
                + "Void main() { Box<String> value = empty() }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void preservesNullableGenericArgumentsAndExpectedTypes() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "T? nullable<T>(T value) { return null } "
                + "Void main() { String? first = identity(value: null) "
                + "String? second = nullable(value: \"Norm\") "
                + "List<String?> values = List<String?>() values.add(first) values.add(second) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsRawGenericTypes() {
    CompilationResult result = compile("Void main() { List values = List<Integer>() }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("requires 1 type argument")));
  }

  @Test
  void rejectsMismatchedInvariantTypeArguments() {
    CompilationResult result =
        compile("Void main() { List<Integer> values = List<String>() printLine(values.size()) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void infersEmptyArrayLiteralsFromTheExpectedType() {
    CompilationResult result =
        compile("Void main() { Array<String> values = [] printLine(values.size()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsTooManyTypeArguments() {
    CompilationResult result = compile("Void main() { List<Integer, String> values = [] }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsConflictingGenericInference() {
    CompilationResult result =
        compile(
            "Boolean same<T>(T left, T right) { return left == right } "
                + "Void main() { printLine(same(left: 1, right: \"1\")) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsValuesThatConflictWithExplicitTypeArguments() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "Void main() { printLine(identity<String>(value: 1)) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsNestedInvariantTypeMismatches() {
    CompilationResult result =
        compile("Void main() { List<List<Integer>> values = List<List<String>>() }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsUndeclaredTypeParameters() {
    CompilationResult result = compile("T identity(T value) { return value } Void main() {}");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsUnknownExplicitTypeArguments() {
    CompilationResult constructor = compile("class Marker<T> {} Void main() { Marker<Missing>() }");
    CompilationResult function =
        compile(
            "T create<T>(T value) { return value } " + "Void main() { create<Missing>(value: 1) }");

    assertFalse(constructor.isSuccess());
    assertFalse(function.isSuccess());
    assertTrue(
        constructor.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("unknown type 'Missing'")),
        () -> constructor.diagnostics().toString());
    assertTrue(
        function.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("unknown type 'Missing'")),
        () -> function.diagnostics().toString());
  }

  @Test
  void rejectsTypeArgumentsOnNonGenericBuiltins() {
    CompilationResult result = compile("Void main() { printLine<Integer>(1) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("requires 0 type argument")));
  }

  @Test
  void rejectsGenericMethodsInTheVersionZeroTwoSubset() {
    CompilationResult result =
        compile("class Values { T identity<T>(T value) { return value } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().contains("generic methods are not supported")),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
