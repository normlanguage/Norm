package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FunctionCompilerTest {
  @Test
  void treatsOmittedTopLevelReturnsAsVoid() {
    var valid = compile("run() { return } discard<T>(T value) { } main() { run() discard(1) }");
    var invalid = compile("value() { return 1 } main() { }");

    assertTrue(valid.isSuccess(), () -> valid.diagnostics().toString());
    assertFalse(invalid.isSuccess());
    assertTrue(
        invalid.diagnostics().stream()
            .anyMatch(value -> value.message().contains("expected Void but found Integer")));
  }

  @Test
  void distinguishesFluentMethodsFromExplicitVoidMethods() {
    var fluent =
        compile(
            "class Counter { Integer value add(Integer amount) { value = value + amount } } "
                + "main() { Counter counter = Counter(value: 1) counter.add(2).add(3) }");
    var explicitVoid =
        compile(
            "class Counter { Void clear() { } } "
                + "main() { Counter counter = Counter() counter.clear().clear() }");
    var explicitResult = compile("class Counter { reset() { return Counter() } } main() { }");

    assertTrue(fluent.isSuccess(), () -> fluent.diagnostics().toString());
    assertFalse(explicitVoid.isSuccess());
    assertFalse(explicitResult.isSuccess());
  }

  @Test
  void rejectsReassignmentBeforeOrAfterCapture() {
    var before =
        compile(
            "Void main() { Integer factor = 2 factor = 3 "
                + "var multiply = (Integer value) { value * factor } }");
    var after =
        compile(
            "Void main() { Integer factor = 2 "
                + "var multiply = (Integer value) { value * factor } factor = 3 }");

    assertFalse(before.isSuccess());
    assertFalse(after.isSuccess());
    assertTrue(
        before.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
    assertTrue(
        after.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
  }

  @Test
  void rejectsAssignmentToCapturedLocalInsideLambda() {
    var compilation =
        compile(
            "Void main() { Integer factor = 2 "
                + "var multiply = (Integer value) { factor = value value } }");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
  }

  @Test
  void requiresCompleteFunctionSignaturesAndLambdaArity() {
    var raw = compile("Void main() { Function value = (Integer item) { item } }");
    var arity =
        compile("Void main() { Function<Integer(Integer)> value = (left, right) { left } }");
    var voidParameter = compile("main() { Function<Void(Void)> invalid = (value) { return } }");

    assertFalse(raw.isSuccess());
    assertFalse(arity.isSuccess());
    assertFalse(voidParameter.isSuccess());
  }

  @Test
  void requiresExplicitResolutionForConflictingInterfaceDefaults() {
    var compilation =
        compile(
            "interface First { Integer value() { return 1 } } "
                + "interface Second { Integer value() { return 2 } } "
                + "class Both implements First, Second { } Void main() { }");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("default methods conflict")));
  }

  private dev.w0fv1.norm.value.CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("functions.norm"), text));
  }
}
