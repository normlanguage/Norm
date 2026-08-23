package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LexerTest {
  @Test
  void lexesTypeNamesAsIdentifiers() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(
                SourceFile.of(Path.of("types.norm"), "Integer Boolean String Void Array List"),
                diagnostics)
            .lex();

    assertFalse(diagnostics.hasErrors());
    assertTrue(
        tokens.stream()
            .filter(token -> token.kind() != TokenKind.END_OF_FILE)
            .allMatch(token -> token.kind() == TokenKind.IDENTIFIER));
  }

  @Test
  void lexesTheHelloWorldSurface() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(
                SourceFile.of(Path.of("hello.norm"), "Void main() { printLine(\"Hello\\nNorm\") }"),
                diagnostics)
            .lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(
        List.of(
            TokenKind.IDENTIFIER,
            TokenKind.IDENTIFIER,
            TokenKind.LEFT_PAREN,
            TokenKind.RIGHT_PAREN,
            TokenKind.LEFT_BRACE,
            TokenKind.IDENTIFIER,
            TokenKind.LEFT_PAREN,
            TokenKind.STRING,
            TokenKind.RIGHT_PAREN,
            TokenKind.RIGHT_BRACE,
            TokenKind.END_OF_FILE),
        tokens.stream().map(Token::kind).toList());
    assertEquals("Hello\nNorm", tokens.get(7).value());
  }

  @Test
  void supportsNestedBlockComments() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(
                SourceFile.of(Path.of("comments.norm"), "/* outer /* inner */ done */ Void"),
                diagnostics)
            .lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(TokenKind.IDENTIFIER, tokens.getFirst().kind());
  }

  @Test
  void reportsInvalidCharactersAndUnterminatedStrings() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    new Lexer(SourceFile.of(Path.of("bad.norm"), "@ \"open"), diagnostics).lex();

    assertTrue(diagnostics.hasErrors());
    assertEquals(2, diagnostics.size());
  }

  @Test
  void rejectsInterpolationUntilItsParserAndIrAreImplemented() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    new Lexer(SourceFile.of(Path.of("future.norm"), "\"Hello ${name}\""), diagnostics).lex();

    assertTrue(diagnostics.hasErrors());
    assertEquals("NORM-LEXER-0005", diagnostics.snapshot().getFirst().code().value());
  }
}
