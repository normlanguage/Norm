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
  void keepsValueAvailableAsAnIdentifier() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(SourceFile.of(Path.of("value.norm"), "value"), diagnostics).lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(TokenKind.IDENTIFIER, tokens.getFirst().kind());
  }

  @Test
  void preservesIdentifierSpellingAndCanonicalizesItsSemanticValueToNfc() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(SourceFile.of(Path.of("unicode.norm"), "e\u0301"), diagnostics).lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals("e\u0301", tokens.getFirst().lexeme());
    assertEquals("\u00e9", tokens.getFirst().value());
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
  void lexesUnicodeCodePointLiterals() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(SourceFile.of(Path.of("code-points.norm"), "'a' '😀' '\\n'"), diagnostics).lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(
        List.of(TokenKind.CODE_POINT, TokenKind.CODE_POINT, TokenKind.CODE_POINT),
        tokens.stream()
            .filter(token -> token.kind() != TokenKind.END_OF_FILE)
            .map(Token::kind)
            .toList());
    assertEquals("97", tokens.getFirst().value());
    assertEquals("128512", tokens.get(1).value());
    assertEquals("10", tokens.get(2).value());
  }

  @Test
  void lexesNullableTypesAndOperators() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(
                SourceFile.of(
                    Path.of("nullable.norm"),
                    "String? value = null value?.codePointSize() value ?? \"\""),
                diagnostics)
            .lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(
        List.of(
            TokenKind.IDENTIFIER,
            TokenKind.QUESTION,
            TokenKind.IDENTIFIER,
            TokenKind.EQUAL,
            TokenKind.NULL,
            TokenKind.IDENTIFIER,
            TokenKind.QUESTION_DOT,
            TokenKind.IDENTIFIER,
            TokenKind.LEFT_PAREN,
            TokenKind.RIGHT_PAREN,
            TokenKind.IDENTIFIER,
            TokenKind.QUESTION_QUESTION,
            TokenKind.STRING,
            TokenKind.END_OF_FILE),
        tokens.stream().map(Token::kind).toList());
  }

  @Test
  void rejectsEmptyAndMultipleCodePointLiterals() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    new Lexer(SourceFile.of(Path.of("bad-code-points.norm"), "'' 'ab'"), diagnostics).lex();

    assertTrue(diagnostics.hasErrors());
    assertEquals(2, diagnostics.size());
  }

  @Test
  void rejectsSurrogateCodePointLiterals() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    String source = "'" + Character.MIN_SURROGATE + "'";

    new Lexer(SourceFile.of(Path.of("surrogate.norm"), source), diagnostics).lex();

    assertTrue(diagnostics.hasErrors());
    assertEquals(1, diagnostics.size());
  }

  @Test
  void treatsCommentMarkersAsOperators() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens =
        new Lexer(SourceFile.of(Path.of("operators.norm"), "// /* */"), diagnostics).lex();

    assertFalse(diagnostics.hasErrors());
    assertEquals(
        List.of(
            TokenKind.SLASH,
            TokenKind.SLASH,
            TokenKind.SLASH,
            TokenKind.STAR,
            TokenKind.STAR,
            TokenKind.SLASH,
            TokenKind.END_OF_FILE),
        tokens.stream().map(Token::kind).toList());
  }

  @Test
  void reportsInvalidCharactersAndUnterminatedStrings() {
    DiagnosticBag diagnostics = new DiagnosticBag();
    new Lexer(SourceFile.of(Path.of("bad.norm"), "` \"open"), diagnostics).lex();

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
