package dev.w0fv1.norm.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DiagnosticTest {
  private static final DiagnosticCode TEST_CODE = new DiagnosticCode("NORM-TEST-0001");

  @Test
  void validatesStableCodes() {
    assertEquals("NORM-TEST-0001", TEST_CODE.toString());
    assertThrows(IllegalArgumentException.class, () -> new DiagnosticCode("test-1"));
  }

  @Test
  void ownsImmutableCopiesOfCollections() {
    SourceSpan span = span("name");
    List<String> notes = new ArrayList<>(List.of("first"));
    Diagnostic diagnostic =
        new Diagnostic(TEST_CODE, DiagnosticSeverity.WARNING, "message", span, List.of(), notes);

    notes.add("second");

    assertEquals(List.of("first"), diagnostic.notes());
    assertThrows(UnsupportedOperationException.class, () -> diagnostic.notes().add("third"));
  }

  private static SourceSpan span(String text) {
    SourceFile source = SourceFile.of(Path.of("test.norm"), text);
    return new SourceSpan(source, 0, text.length());
  }
}
