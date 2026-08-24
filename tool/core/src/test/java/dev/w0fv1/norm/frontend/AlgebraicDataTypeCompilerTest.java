package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AlgebraicDataTypeCompilerTest {
  @Test
  void compilesGenericDataEnumsAndRecursivePatterns() {
    CompilationResult result =
        compile(
            "enum Result<T, E> { Ok(T value), Err(E error) } "
                + "enum Tree<T> { Leaf(T value), Branch(Tree<T> left, Tree<T> right) } "
                + "Integer sum(Tree<Integer> tree) { return switch tree { "
                + "case Leaf(Integer value) { break value } "
                + "case Branch(Leaf(Integer left), Tree<Integer> right) { break left + sum(tree: right) } "
                + "case Branch(Tree<Integer> left, Tree<Integer> right) { break sum(tree: left) + sum(tree: right) } "
                + "} } "
                + "Void main() { Result<Integer, String> result = Result<Integer, String>.Ok(value: 7) "
                + "Tree<Integer> tree = Tree<Integer>.Branch(left: Tree<Integer>.Leaf(value: 2), "
                + "right: Tree<Integer>.Leaf(value: 3)) printLine(sum(tree: tree)) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void infersGenericEnumArgumentsFromPayloadAndExpectedType() {
    CompilationResult result =
        compile(
            "enum Result<T, E> { Ok(T value), Err(E error) } "
                + "Void main() { Result<Integer, String> ok = Result.Ok(value: 7) "
                + "Result<Integer, String> error = Result.Err(error: \"invalid\") } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsLiteralNullWildcardAndTypedBindingPatterns() {
    CompilationResult result =
        compile(
            "String describe(Integer? value) { return switch value { "
                + "case null { break \"missing\" } case 0 { break \"zero\" } "
                + "case Integer number { break \"number\" } } } "
                + "String classify(String text) { return switch text { "
                + "case \"Norm\" { break \"language\" } case _ { break \"other\" } } } "
                + "Void main() { printLine(describe(value: null)) printLine(classify(text: \"Norm\")) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNonExhaustiveAndUnreachableSwitches() {
    CompilationResult nonExhaustive =
        compile(
            "enum Choice { First, Second } Void main() { Choice choice = Choice.First "
                + "switch choice { case First {} } } ");
    CompilationResult unreachable =
        compile(
            "enum Choice { First, Second } Void main() { Choice choice = Choice.First "
                + "switch choice { case _ {} case First {} } } ");

    assertFalse(nonExhaustive.isSuccess());
    assertFalse(unreachable.isSuccess());
    assertTrue(
        nonExhaustive.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("not exhaustive")),
        () -> nonExhaustive.diagnostics().toString());
    assertTrue(
        unreachable.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("unreachable")),
        () -> unreachable.diagnostics().toString());
  }

  @Test
  void requiresEverySwitchExpressionPathToBreakWithACompatibleValue() {
    CompilationResult missing =
        compile(
            "enum Choice { First, Second } String text(Choice choice) { return switch choice { "
                + "case First { break \"first\" } case Second {} } } Void main() {} ");
    CompilationResult incompatible =
        compile(
            "enum Choice { First, Second } String text(Choice choice) { return switch choice { "
                + "case First { break \"first\" } case Second { break 2 } } } Void main() {} ");

    assertFalse(missing.isSuccess());
    assertFalse(incompatible.isSuccess());
  }

  private static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
