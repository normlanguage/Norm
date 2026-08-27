package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReferenceCompilerTest {
  @Test
  void compilesAddressReadWriteCopyAndIdentityEquality() {
    CompilationResult result =
        compile(
            "Void increment(ref<Integer> value) { *value = *value + 1 } "
                + "Void main() { Integer count = 1 ref<Integer> first = &count "
                + "ref<Integer> second = first increment(value: first) "
                + "printLine(*second) printLine(first == second) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNonValueAndNestedReferenceTargets() {
    CompilationResult classTarget =
        compile("class Box {} Void use(ref<Box> value) {} Void main() {}");
    CompilationResult nested = compile("Void use(ref<ref<Integer>> value) {} Void main() {}");

    assertFalse(classTarget.isSuccess());
    assertFalse(nested.isSuccess());
  }

  @Test
  void rejectsReferencesInEscapingTypePositions() {
    CompilationResult returned =
        compile("ref<Integer> invalid(ref<Integer> value) { return value } Void main() {}");
    CompilationResult field = compile("class Invalid { ref<Integer> value } Void main() {}");
    CompilationResult generic = compile("Void main() { List<ref<Integer>> values = [] }");

    assertFalse(returned.isSuccess());
    assertFalse(field.isSuccess());
    assertFalse(generic.isSuccess());
  }

  @Test
  void rejectsTemporaryAddressesAndLambdaCapture() {
    CompilationResult temporary = compile("Void main() { ref<Integer> value = &1 }");
    CompilationResult capture =
        compile(
            "Void use(ref<Integer> value) { "
                + "Function<Integer()> read = () { *value } printLine(read()) } "
                + "Void main() { Integer value = 1 use(value: &value) }");

    assertFalse(temporary.isSuccess());
    assertFalse(capture.isSuccess());
  }

  @Test
  void rejectsReferencesThroughGenericCalls() {
    CompilationResult explicit =
        compile(
            "T identity<T>(T value) { return value } "
                + "Void main() { Integer value = 1 ref<Integer> result = "
                + "identity<ref<Integer>>(value: &value) }");
    CompilationResult inferred =
        compile(
            "T identity<T>(T value) { return value } "
                + "Void main() { Integer value = 1 ref<Integer> result = "
                + "identity(value: &value) }");
    CompilationResult nullableParameter =
        compile(
            "Void accept<T>(T? value) {} Void main() { Integer value = 1 "
                + "accept<ref<Integer>>(value: &value) }");
    CompilationResult nullableField =
        compile(
            "class Box<T> { T? value } Void main() { Integer value = 1 "
                + "Box<ref<Integer>> box = Box<ref<Integer>>(value: &value) "
                + "printLine(box.value) }");

    assertFalse(explicit.isSuccess());
    assertFalse(inferred.isSuccess());
    assertFalse(nullableParameter.isSuccess());
    assertFalse(nullableField.isSuccess());
  }

  @Test
  void rejectsTakingTheAddressOfAnOuterLambdaLocal() {
    CompilationResult result =
        compile(
            "Void main() { Integer outer = 1 Function<Void()> mutate = () { "
                + "ref<Integer> location = &outer *location = 2 } mutate() }");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsInferredReferenceReturnsFromLambdas() {
    CompilationResult local =
        compile("Void main() { var invalid = () { Integer value = 1 &value } }");
    CompilationResult field =
        compile(
            "class Box { Integer value } Void main() { Box box = Box(value: 1) "
                + "var invalid = () { &box.value } }");

    for (CompilationResult result : java.util.List.of(local, field)) {
      assertFalse(result.isSuccess());
      assertTrue(
          result.diagnostics().stream()
              .anyMatch(diagnostic -> diagnostic.message().contains("cannot contain ref")),
          () -> result.diagnostics().toString());
    }
  }

  @Test
  void rejectsInferredCollectionsOfReferences() {
    CompilationResult result = compile("Void main() { Integer value = 1 var invalid = [&value] }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.message().contains("element type cannot contain ref")),
        () -> result.diagnostics().toString());
  }

  @Test
  void diagnosesNullableOperatorsWithoutCreatingNullableReferences() {
    CompilationResult result =
        compile(
            "Void main() { Integer value = 1 ref<Integer> first = &value "
                + "ref<Integer> second = first ?? first }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("left side of ??")));
  }

  @Test
  void rejectsReferencesThatOutliveLexicalStorage() {
    CompilationResult direct =
        compile(
            "Void main() { Integer fallback = 0 ref<Integer> location = &fallback "
                + "if true { Integer inner = 2 location = &inner } printLine(*location) }");
    CompilationResult copied =
        compile(
            "Void main() { Integer fallback = 0 ref<Integer> outer = &fallback "
                + "if true { Integer inner = 2 ref<Integer> nested = &inner outer = nested } }");
    CompilationResult switched =
        compile(
            "enum Choice { First, Second } Void main() { Integer fallback = 0 "
                + "Choice choice = Choice.First ref<Integer> location = switch choice { "
                + "case First { Integer inner = 2 break &inner } "
                + "case Second { break &fallback } } printLine(*location) }");

    assertLifetimeDiagnostic(direct);
    assertLifetimeDiagnostic(copied);
    assertLifetimeDiagnostic(switched);
  }

  @Test
  void rejectsExpiredSwitchReferencesUsedWithoutARefBinding() {
    CompilationResult result =
        compile(
            "enum Choice { First, Second } Void consume(ref<Integer> value) {} "
                + "Void main() { Integer fallback = 0 Choice choice = Choice.First consume(value: "
                + "switch choice { case First { Integer inner = 2 break &inner } "
                + "case Second { break &fallback } }) }");

    assertLifetimeDiagnostic(result);
  }

  @Test
  void appliesExplicitFinallyReferenceWritesToCoreFlow() {
    CompilationResult result =
        compile(
            "Void main() { Integer first = 1 ref<Integer> outer = &first if true { "
                + "Integer second = 2 ref<Integer> current = &first try { current = &second } "
                + "finally { current = &first } outer = current } printLine(*outer) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsLambdaControlFlowSeparateFromEnclosingSwitches() {
    CompilationResult result =
        compile(
            "enum Choice { First } Void main() { Choice choice = Choice.First "
                + "Integer value = switch choice { case First { "
                + "Function<Void()> invalid = () { break 1 } break 2 } } printLine(value) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("break is only valid")),
        () -> result.diagnostics().toString());
  }

  private static void assertLifetimeDiagnostic(CompilationResult result) {
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic
                        .message()
                        .equals("reference cannot outlive the addressed storage location")),
        () -> result.diagnostics().toString());
    assertTrue(
        result.diagnostics().stream()
            .noneMatch(diagnostic -> diagnostic.code().value().equals("NORM-NAME-0003")),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("references.norm"), text));
  }
}
