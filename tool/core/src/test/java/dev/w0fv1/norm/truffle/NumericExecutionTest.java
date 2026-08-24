package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NumericExecutionTest {
  @Test
  void executesEveryNumericLeafAndPreservesNumberLeafValues() throws Exception {
    String source =
        "Void main() { Integer integer = -2147483648 Long longValue = -9223372036854775808 "
            + "Float floatValue = 1.25 Double doubleValue = 2.5 Number number = 2147483648 "
            + "printLine(integer + 1) printLine(longValue + 1) "
            + "printLine(floatValue * 2.0) printLine(doubleValue / 2.0) printLine(number) }";
    var compilation = new Compiler().compile(SourceFile.of(Path.of("numeric.norm"), source));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals(
        String.join(
            System.lineSeparator(),
            "-2147483647",
            "-9223372036854775807",
            "2.5",
            "1.25",
            "2147483648",
            ""),
        output.toString());
  }

  @Test
  void evaluatesNumericComparisonsWithinEachLeaf() throws Exception {
    String source =
        "Void main() { printLine(1 < 2) Long left = 3 Long right = 2 "
            + "printLine(left > right) Float low = 1.0 Float high = 2.0 "
            + "printLine(low <= high) Double value = 4.0 printLine(value == 4.0) }";
    var compilation = new Compiler().compile(SourceFile.of(Path.of("numeric.norm"), source));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals(
        String.join(System.lineSeparator(), "true", "true", "true", "true", ""), output.toString());
  }

  @Test
  void decimalComparisonsFollowIeeeSemantics() throws Exception {
    String source =
        "Void main() { Double zero = 0.0 Double nan = zero / zero "
            + "printLine(nan == nan) printLine(nan < zero) printLine(nan >= zero) }";
    var compilation = new Compiler().compile(SourceFile.of(Path.of("numeric.norm"), source));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals(
        String.join(System.lineSeparator(), "false", "false", "false", ""), output.toString());
  }
}
