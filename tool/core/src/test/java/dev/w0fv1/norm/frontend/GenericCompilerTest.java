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
                + "void main() { Box<List<int>> box = Box<List<int>>(value: List<int>()) "
                + "box.value.add(7) print(box.value[0]) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericFunctionArgumentsExactly() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "void main() { String value = identity(value: \"Norm\") print(value) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericFunctionArgumentsFromTheExpectedType() {
    CompilationResult result =
        compile(
            "class Box<T> {} Box<T> empty<T>() { return Box<T>() } "
                + "void main() { Box<String> value = empty() }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsRawGenericTypes() {
    CompilationResult result = compile("void main() { List values = List<int>() }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("requires 1 type argument")));
  }

  @Test
  void rejectsMismatchedInvariantTypeArguments() {
    CompilationResult result =
        compile("void main() { List<int> values = List<String>() print(values.size()) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void infersEmptyArrayLiteralsFromTheExpectedType() {
    CompilationResult result =
        compile("void main() { Array<String> values = [] print(values.size()) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsTooManyTypeArguments() {
    CompilationResult result = compile("void main() { List<int, String> values = [] }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsConflictingGenericInference() {
    CompilationResult result =
        compile(
            "bool same<T>(T left, T right) { return left == right } "
                + "void main() { print(same(left: 1, right: \"1\")) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsValuesThatConflictWithExplicitTypeArguments() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "void main() { print(identity<String>(value: 1)) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsNestedInvariantTypeMismatches() {
    CompilationResult result =
        compile("void main() { List<List<int>> values = List<List<String>>() }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsUndeclaredTypeParameters() {
    CompilationResult result = compile("T identity(T value) { return value } void main() {}");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsUnknownExplicitTypeArguments() {
    CompilationResult constructor = compile("class Marker<T> {} void main() { Marker<Missing>() }");
    CompilationResult function =
        compile(
            "T create<T>(T value) { return value } " + "void main() { create<Missing>(value: 1) }");

    assertFalse(constructor.isSuccess());
    assertFalse(function.isSuccess());
    assertTrue(
        constructor.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().contains("unknown or invalid type 'Missing'")),
        () -> constructor.diagnostics().toString());
    assertTrue(
        function.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().contains("unknown or invalid type 'Missing'")),
        () -> function.diagnostics().toString());
  }

  @Test
  void rejectsTypeArgumentsOnNonGenericBuiltins() {
    CompilationResult result = compile("void main() { print<int>(1) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("requires 0 type argument")));
  }

  @Test
  void rejectsGenericMethodsInTheVersionZeroTwoSubset() {
    CompilationResult result =
        compile("class Values { T identity<T>(T value) { return value } } void main() {}");

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
