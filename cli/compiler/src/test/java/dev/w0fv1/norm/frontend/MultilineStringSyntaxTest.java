package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.syntax.TokenKind;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MultilineStringSyntaxTest {
  @Test
  void acceptsOpeningAndClosingDelimitersOnTheirOwnLines() {
    var parsed = SourceParser.parse(SourceFile.of(Path.of("valid.norm"), validSource()));

    assertTrue(parsed.diagnostics().isEmpty(), () -> parsed.diagnostics().toString());
  }

  @Test
  void preservesInterpolationAndDelimiterIndentationAtRuntime() {
    String source =
        "Void main() {\n"
            + "  String name = \"Norm\"\n"
            + "  String instructions = \"\"\"\n"
            + "    hello ${name}\n"
            + "    world\n"
            + "  \"\"\"\n"
            + "  printLine(instructions)\n"
            + "}\n";
    var formatted =
        new SourceFormatter()
            .format(SourceFile.of(Path.of("interpolation.norm"), source))
            .orElseThrow();
    var reparsed = SourceParser.parse(SourceFile.of(Path.of("interpolation.norm"), formatted));

    assertTrue(reparsed.diagnostics().isEmpty(), () -> reparsed.diagnostics().toString());
    String output = dev.w0fv1.norm.testing.NormTestKit.run(formatted).replace("\r\n", "\n");
    assertEquals("\n    hello Norm\n    world\n  \n", output);
  }

  @Test
  void acceptsCrLfLineEndings() {
    String source = validSource().replace("\n", "\r\n");
    var parsed = SourceParser.parse(SourceFile.of(Path.of("crlf.norm"), source));

    assertTrue(parsed.diagnostics().isEmpty(), () -> parsed.diagnostics().toString());
  }

  @Test
  void acceptsAClosingDelimiterAtEndOfFile() {
    var diagnostics = new DiagnosticBag();
    var tokens =
        new Lexer(SourceFile.of(Path.of("eof.norm"), "\"\"\"\ncontent\n\"\"\""), diagnostics).lex();
    var string = tokens.stream().filter(token -> token.kind() == TokenKind.STRING).findFirst();

    assertFalse(diagnostics.hasErrors(), () -> diagnostics.snapshot().toString());
    assertTrue(string.isPresent());
    assertEquals("\ncontent\n", string.orElseThrow().value());
  }

  @Test
  void rejectsTextAfterTheOpeningDelimiter() {
    var parsed =
        SourceParser.parse(
            SourceFile.of(
                Path.of("invalid-opening.norm"),
                "Void main() {\n"
                    + "  String instructions = \"\"\"instructions\n"
                    + "instructions instructions\n"
                    + "\"\"\"\n"
                    + "}\n"));

    assertFalse(parsed.diagnostics().isEmpty());
    assertTrue(
        parsed.diagnostics().stream().anyMatch(d -> d.code().value().equals("NORM-LEXER-0008")),
        () -> parsed.diagnostics().toString());
  }

  @Test
  void rejectsTextAfterTheClosingDelimiter() {
    var parsed =
        SourceParser.parse(
            SourceFile.of(
                Path.of("invalid-closing.norm"),
                "Void main() {\n"
                    + "  String instructions = \"\"\"\n"
                    + "instructions instructions\n"
                    + "instructions \"\"\"\n"
                    + "}\n"));

    assertFalse(parsed.diagnostics().isEmpty());
    assertTrue(
        parsed.diagnostics().stream().anyMatch(d -> d.code().value().equals("NORM-LEXER-0009")),
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
