package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NumericTypeCompilerTest {
  @Test
  void resolvesLiteralDefaultsAndConcreteExpectedTypes() {
    CompilationResult result =
        compile(
            "Void main() { Integer integer = 2147483647 Long inferredLong = 2147483648 "
                + "Long expectedLong = 1 Float expectedFloat = 1.25 Double decimal = 1.25 "
                + "Number integerNumber = 1 Number decimalNumber = 1.25 }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsSignedMinimumValues() {
    CompilationResult result =
        compile(
            "Void main() { Integer integer = -2147483648 Long longValue = -9223372036854775808 }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNumericLiteralsOutsideTheirExpectedLeafRange() {
    CompilationResult integer = compile("Void main() { Integer value = 2147483648 }");
    CompilationResult longValue = compile("Void main() { Long value = 9223372036854775808 }");
    CompilationResult floatValue = compile("Void main() { Float value = 1e100 }");
    CompilationResult doubleValue = compile("Void main() { Double value = 1e10000 }");

    assertFalse(integer.isSuccess());
    assertFalse(longValue.isSuccess());
    assertFalse(floatValue.isSuccess());
    assertFalse(doubleValue.isSuccess());
  }

  @Test
  void keepsNonLiteralNumericLeavesInvariant() {
    CompilationResult result = compile("Void main() { Integer source = 1 Long target = source }");

    assertFalse(result.isSuccess());
  }

  @Test
  void treatsNumberAsAnAbstractCommonType() {
    CompilationResult assignment =
        compile(
            "Void main() { Number first = 1 Number second = 2147483648 " + "Number third = 1.5 }");
    CompilationResult construction = compile("Void main() { Number value = Number() }");

    assertTrue(assignment.isSuccess(), () -> assignment.diagnostics().toString());
    assertFalse(construction.isSuccess());
  }

  @Test
  void validatesNumericSeparatorPlacement() {
    assertTrue(compile("Void main() { Long value = 9_223_372_036_854_775_807 }").isSuccess());
    assertFalse(compile("Void main() { Integer value = 1_ }").isSuccess());
    assertFalse(compile("Void main() { Double value = 1_.5 }").isSuccess());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("numeric.norm"), text));
  }
}
