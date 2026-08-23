package dev.w0fv1.norm.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DiagnosticRendererTest {
  @Test
  void rendersAStableSourceExcerpt() {
    SourceFile source = SourceFile.of(Path.of("sample.norm"), "Integer answer = nope\n");
    Diagnostic diagnostic =
        Diagnostic.error(
                new DiagnosticCode("NORM-NAME-0001"),
                "cannot find name 'nope'",
                new SourceSpan(source, 13, 17))
            .withNote("declare the name before using it");

    String newline = System.lineSeparator();
    assertEquals(
        "sample.norm:1:14: error[NORM-NAME-0001]: cannot find name 'nope'"
            + newline
            + "Integer answer = nope"
            + newline
            + "             ^^^^"
            + newline
            + "note: declare the name before using it",
        DiagnosticRenderer.render(diagnostic));
  }
}
