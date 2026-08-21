package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.diagnostic.DiagnosticSeverity;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DiagnosticBagTest {
  private static final DiagnosticCode TEST_CODE = new DiagnosticCode("NORM-TEST-0001");

  @Test
  void tracksErrorsAndReturnsImmutableSnapshots() {
    DiagnosticBag bag = new DiagnosticBag();
    bag.report(
        new Diagnostic(
            TEST_CODE, DiagnosticSeverity.INFO, "information", span("x"), List.of(), List.of()));
    bag.error(TEST_CODE, "failure", span("y"));

    assertEquals(2, bag.size());
    assertTrue(bag.hasErrors());
    assertThrows(UnsupportedOperationException.class, () -> bag.snapshot().clear());
  }

  private static SourceSpan span(String text) {
    SourceFile source = SourceFile.of(Path.of("test.norm"), text);
    return new SourceSpan(source, 0, text.length());
  }
}
