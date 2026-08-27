package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ValueCompilerTest {
  @Test
  void compilesGenericValuesWithMethodsAndInterfaceConformance() {
    CompilationResult result =
        compile(
            "interface Readable<T> { T read() } "
                + "value Box<T> implements Readable<T> { T value public T read() { return value } } "
                + "T read<T>(Readable<T> value) { return value.read() } "
                + "Void main() { Box<Integer> box = Box<>(value: 42) printLine(read(value: box)) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsValueFieldMutation() {
    CompilationResult result =
        compile(
            "value Point { Integer x Integer y } "
                + "Void main() { Point point = Point(x: 1, y: 2) point.x = 3 } ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("value field")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsValueInheritance() {
    CompilationResult result =
        compile(
            "interface Sized { Integer size() } value Invalid extends Sized {} Void main() {} ");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsFieldMutationInsideValueMethods() {
    CompilationResult result =
        compile(
            "value Counter { Integer count Void increment() { count = count + 1 } } "
                + "Void main() {} ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("value field")),
        () -> result.diagnostics().toString());
  }

  @Test
  void valueRemainsAvailableAsAGenericFunctionReturnType() {
    CompilationResult result =
        compile(
            "class value {} value make<T>(T ignored) { return value() } "
                + "Void main() { printLine(make(ignored: 1)) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
