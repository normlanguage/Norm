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
                + "Void main() { Box<List<Integer>> box = Box<>(value: List<>()) "
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
            "class Box<T> {} Box<T> empty<T>() { return Box<>() } "
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
                + "List<String?> values = List<>() values.add(first) values.add(second) } ");

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
  void acceptsInferredAndExplicitGenericMethodArguments() {
    CompilationResult result =
        compile(
            "class Values { T identity<T>(T value) { return value } } "
                + "Void main() { Values values = Values() "
                + "String inferred = values.identity(value: \"Norm\") "
                + "Integer explicit = values.identity<Integer>(value: 7) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsOwnerAndMethodTypeParametersDistinct() {
    CompilationResult result =
        compile(
            "class Values<T> { T owner Pair<T, U> pair<U>(U value) { "
                + "return Pair<>(first: owner, second: value) } } "
                + "Void main() { Values<String> values = Values<String>(owner: \"Norm\") "
                + "Pair<String, Integer> pair = values.pair(value: 7) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsGenericMethodsWhoseTypeArgumentCannotBeInferred() {
    CompilationResult result =
        compile("class Values { Void ping<T>() {} } Void main() { Values().ping() }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().contains("cannot infer type argument 'T'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsValuesThatConflictWithExplicitMethodTypeArguments() {
    CompilationResult result =
        compile(
            "class Values { T identity<T>(T value) { return value } } "
                + "Void main() { Values().identity<String>(value: 1) }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsTypeArgumentsOnNonGenericMethods() {
    CompilationResult result =
        compile("class Values { Void clear() {} } Void main() { Values().clear<Integer>() }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("requires 0 type argument")),
        () -> result.diagnostics().toString());
  }

  @Test
  void acceptsExplicitTypeArgumentsOnGenericBuiltinTypeMethods() {
    CompilationResult result =
        compile(
            "Void main() { List<Integer> values = " + "List.filled<Integer>(size: 2, value: 7) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void selectsGenericMethodOverloadsByExplicitTypeArity() {
    CompilationResult result =
        compile(
            "class Values { String pick(String? value) { return \"plain\" } "
                + "T? pick<T>(T? value) { return value } } "
                + "Void main() { Integer? value = Values().pick<Integer>(value: null) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void resolvesNestedGenericParametersBeforeRankingOverloads() {
    CompilationResult result =
        compile(
            "class Values { String pick(String value) { return value } "
                + "T pick<T>(List<T> values) { return values[0] } } "
                + "Void main() { Integer value = Values().pick("
                + "values: List.filled(size: 1, value: 7)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericArgumentsIndependentlyOfArgumentOrder() {
    CompilationResult first =
        compile(
            "T? choose<T>(T? optional, T required) { return optional } "
                + "Void main() { String? value = choose(optional: null, required: \"Norm\") }");
    CompilationResult second =
        compile(
            "T? choose<T>(T required, T? optional) { return optional } "
                + "Void main() { String? value = choose(required: \"Norm\", optional: null) }");

    assertTrue(first.isSuccess(), () -> first.diagnostics().toString());
    assertTrue(second.isSuccess(), () -> second.diagnostics().toString());
  }

  @Test
  void joinsNullableAndNonNullableConstraintsForOneTypeParameter() {
    CompilationResult result =
        compile(
            "T choose<T>(T first, T second) { return second } "
                + "Void main() { String? optional = null "
                + "String? value = choose(first: \"Norm\", second: optional) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void contextuallyTypesNestedGenericCalls() {
    CompilationResult explicit =
        compile(
            "class Values { T identity<T>(T value) { return value } } "
                + "Void main() { Values values = Values() "
                + "String? result = values.identity<String?>("
                + "value: values.identity(value: null)) }");
    CompilationResult inferred =
        compile(
            "class Values { T identity<T>(T value) { return value } } "
                + "Void main() { Values values = Values() "
                + "String? result = values.identity("
                + "value: values.identity(value: null)) }");

    assertTrue(explicit.isSuccess(), () -> explicit.diagnostics().toString());
    assertTrue(inferred.isSuccess(), () -> inferred.diagnostics().toString());
  }

  @Test
  void excludesInaccessibleMethodsBeforeRankingOverloads() {
    CompilationResult result =
        compile(
            "class Values { private String pick(String value) { return \"private\" } "
                + "public T pick<T>(T value) { return value } } "
                + "Void main() { String value = Values().pick(value: \"public\") }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsAllVisibleTopLevelOverloadsInOneFile() {
    CompilationResult result =
        compile(
            "private Integer pick(Integer value) { return value } "
                + "public T pick<T>(T value) { return value } "
                + "Void main() { String value = pick(value: \"Norm\") }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsPrivateMethodsAvailableInsideTheirClass() {
    CompilationResult result =
        compile(
            "class Values { private String pick(String value) { return value } "
                + "public T pick<T>(T value) { return value } "
                + "String local() { return this.pick(value: \"Norm\") } } "
                + "Void main() { String value = Values().local() }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersNestedTypeParametersFromArrayLiteralElements() {
    CompilationResult result =
        compile(
            "T first<T>(Array<T> values) { return values[0] } "
                + "Void main() { printLine(first(values: [1, 2])) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersNestedTypeParametersThroughArrayLiteralCalls() {
    CompilationResult result =
        compile(
            "T identity<T>(T value) { return value } "
                + "T first<T>(Array<T> values) { return values[0] } "
                + "Void main() { printLine(first(values: [identity(value: 1)])) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void defersEmptyArrayLiteralsUntilOtherConstraintsResolveTheirElementType() {
    CompilationResult expectedResult =
        compile(
            "Array<T> preserve<T>(Array<T> values) { return values } "
                + "Void main() { Array<Integer> values = preserve(values: []) }");
    CompilationResult siblingArgument =
        compile(
            "Array<T> preserve<T>(Array<T> values, T fallback) { return values } "
                + "Void main() { printLine(preserve(values: [], fallback: 1).size()) }");

    assertTrue(expectedResult.isSuccess(), () -> expectedResult.diagnostics().toString());
    assertTrue(siblingArgument.isSuccess(), () -> siblingArgument.diagnostics().toString());
  }

  @Test
  void defersNullArrayElementsWhileCollectingConcreteElementConstraints() {
    CompilationResult result =
        compile(
            "T? second<T>(Array<T?> values) { return values[1] } "
                + "Void main() { printLine(second(values: [null, \"Norm\"]) ?? \"missing\") }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
