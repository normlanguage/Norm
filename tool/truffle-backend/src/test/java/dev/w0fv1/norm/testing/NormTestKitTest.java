package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NormTestKitTest {
  @TempDir Path temporaryDirectory;

  @Test
  void acceptsInlineExpectedOutputLines() throws Exception {
    Path test = temporaryDirectory.resolve("InlineExpectation.norm");
    Files.writeString(test, "Void main() { printLine(20) expectedOutputLine(\"20\") }");

    assertDoesNotThrow(() -> NormTestKit.assertSelfContainedTest(test));
  }

  @Test
  void reportsOutputMismatches() throws Exception {
    Path test = temporaryDirectory.resolve("Mismatch.norm");
    Files.writeString(test, "Void main() { printLine(20) expectedOutputLine(\"21\") }");

    assertThrows(AssertionError.class, () -> NormTestKit.assertSelfContainedTest(test));
  }

  @Test
  void regularExecutionOnlyEmitsProgramOutput() {
    assertEquals(
        "20" + System.lineSeparator(),
        NormTestKit.run("Void main() { printLine(20) expectedOutputLine(\"20\") }"));
  }
}
