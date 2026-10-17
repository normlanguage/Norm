package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MultilineStringSyntaxTest {
  @Test
  void acceptsOpeningAndClosingDelimitersOnTheirOwnLines() {
    var parsed = SourceParser.parse(SourceFile.of(Path.of("valid.norm"), validSource()));

    assertTrue(parsed.diagnostics().isEmpty(), () -> parsed.diagnostics().toString());
  }

  @Test
  void rejectsTextAfterTheOpeningDelimiter() {
    var parsed = SourceParser.parse(
        SourceFile.of(
            Path.of("invalid-opening.norm"),
            "Void main() {\n"
                + "  String instructions = \"\"\"instructions\n"
                + "instructions instructions\n"
                + "\"\"\"\n"
                + "}\n"));

    assertFalse(parsed.diagnostics().isEmpty());
    assertTrue(
        parsed.diagnostics().stream()
            .anyMatch(d -> d.code().value().equals("NORM-LEXER-0008")),
        () -> parsed.diagnostics().toString());
  }

  @Test
  void rejectsTextAfterTheClosingDelimiter() {
    var parsed = SourceParser.parse(
        SourceFile.of(
            Path.of("invalid-closing.norm"),
            "Void main() {\n"
                + "  String instructions = \"\"\"\n"
                + "instructions instructions\n"
                + "instructions \"\"\"\n"
                + "}\n"));

    assertFalse(parsed.diagnostics().isEmpty());
    assertTrue(
        parsed.diagnostics().stream()
            .anyMatch(d -> d.code().value().equals("NORM-LEXER-0009")),
        () -> parsed.diagnostics().toString());
  }

  private static String validSource() {
    return "Void main() {\n"
        + "  String instructions = \"\"\"\n"
        + "instructions instructions\n"
        + "instructions instructions\n"
        + "\"\"\"\n"
        + "  printLine(instructions)\n"
        + "}\n";
  }
}
