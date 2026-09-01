package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class Lexer {
  private static final DiagnosticCode UNEXPECTED_CHARACTER = new DiagnosticCode("NORM-LEXER-0001");
  private static final DiagnosticCode UNTERMINATED_STRING = new DiagnosticCode("NORM-LEXER-0002");
  private static final DiagnosticCode INVALID_ESCAPE = new DiagnosticCode("NORM-LEXER-0003");
  private static final DiagnosticCode UNSUPPORTED_INTERPOLATION =
      new DiagnosticCode("NORM-LEXER-0005");
  private static final DiagnosticCode INVALID_CODE_POINT = new DiagnosticCode("NORM-LEXER-0006");
  private static final DiagnosticCode INVALID_NUMBER = new DiagnosticCode("NORM-LEXER-0007");

  private final SourceFile source;
  private final DiagnosticBag diagnostics;
  private final CompilationGuard guard;
  private final List<Token> tokens = new ArrayList<>();
  private int offset;

  Lexer(SourceFile source, DiagnosticBag diagnostics) {
    this(source, diagnostics, CompilationGuard.unlimited());
  }

  Lexer(SourceFile source, DiagnosticBag diagnostics, CompilationGuard guard) {
    this.source = Objects.requireNonNull(source, "source");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    this.guard = Objects.requireNonNull(guard, "guard");
  }

  List<Token> lex() {
    while (!isAtEnd()) {
      guard.checkpoint();
      scanToken();
    }
    tokens.add(Token.simple(TokenKind.END_OF_FILE, "", SourceSpan.at(source, offset)));
    return List.copyOf(tokens);
  }

  private void scanToken() {
    int start = offset;
    int character = advanceCodePoint();
    switch (character) {
      case '(' -> addSimple(TokenKind.LEFT_PAREN, start);
      case ')' -> addSimple(TokenKind.RIGHT_PAREN, start);
      case '{' -> addSimple(TokenKind.LEFT_BRACE, start);
      case '}' -> addSimple(TokenKind.RIGHT_BRACE, start);
      case '[' -> addSimple(TokenKind.LEFT_BRACKET, start);
      case ']' -> addSimple(TokenKind.RIGHT_BRACKET, start);
      case ',' -> addSimple(TokenKind.COMMA, start);
      case ';' -> addSimple(TokenKind.SEMICOLON, start);
      case ':' -> addSimple(TokenKind.COLON, start);
      case '.' -> addSimple(TokenKind.DOT, start);
      case '?' ->
          addSimple(
              match('.')
                  ? TokenKind.QUESTION_DOT
                  : match('?') ? TokenKind.QUESTION_QUESTION : TokenKind.QUESTION,
              start);
      case '+' -> addSimple(TokenKind.PLUS, start);
      case '-' -> addSimple(TokenKind.MINUS, start);
      case '*' -> addSimple(TokenKind.STAR, start);
      case '%' -> addSimple(TokenKind.PERCENT, start);
      case '!' -> addSimple(match('=') ? TokenKind.BANG_EQUAL : TokenKind.BANG, start);
      case '=' -> addSimple(match('=') ? TokenKind.EQUAL_EQUAL : TokenKind.EQUAL, start);
      case '<' -> addSimple(match('=') ? TokenKind.LESS_EQUAL : TokenKind.LESS, start);
      case '>' -> addSimple(match('=') ? TokenKind.GREATER_EQUAL : TokenKind.GREATER, start);
      case '&' -> addSimple(match('&') ? TokenKind.AND_AND : TokenKind.AMPERSAND, start);
      case '@' -> addSimple(TokenKind.AT, start);
      case '|' -> scanDoubleOperator('|', TokenKind.OR_OR, start);
      case '"' -> scanString(start);
      case '\'' -> scanCodePoint(start);
      case '/' -> addSimple(TokenKind.SLASH, start);
      default -> {
        if (Character.isWhitespace(character)) {
          return;
        }
        if (isIdentifierStart(character)) {
          scanIdentifier(start);
          return;
        }
        if (Character.isDigit(character)) {
          scanNumber(start);
          return;
        }
        reportUnexpected(character, start);
      }
    }
  }

  private void scanIdentifier(int start) {
    while (!isAtEnd()) {
      int character = source.text().codePointAt(offset);
      if (!Character.isUnicodeIdentifierPart(character)) {
        break;
      }
      offset += Character.charCount(character);
    }
    String lexeme = source.text().substring(start, offset);
    TokenKind kind = LanguageSyntax.tokenKind(lexeme);
    tokens.add(
        new Token(
            kind,
            lexeme,
            kind == TokenKind.IDENTIFIER
                ? Normalizer.normalize(lexeme, Normalizer.Form.NFC)
                : lexeme,
            new SourceSpan(source, start, offset)));
  }

  private void scanNumber(int start) {
    while (!isAtEnd()) {
      int character = source.text().codePointAt(offset);
      if (!Character.isDigit(character) && character != '_') {
        break;
      }
      offset += Character.charCount(character);
    }
    TokenKind kind = TokenKind.INTEGER;
    if (!isAtEnd()
        && source.text().charAt(offset) == '.'
        && offset + 1 < source.length()
        && Character.isDigit(source.text().charAt(offset + 1))) {
      kind = TokenKind.DECIMAL;
      offset++;
      while (!isAtEnd()) {
        int character = source.text().codePointAt(offset);
        if (!Character.isDigit(character) && character != '_') break;
        offset += Character.charCount(character);
      }
    }
    if (!isAtEnd()
        && (source.text().charAt(offset) == 'e' || source.text().charAt(offset) == 'E')) {
      int exponent = offset;
      int cursor = exponent + 1;
      if (cursor < source.length()
          && (source.text().charAt(cursor) == '+' || source.text().charAt(cursor) == '-')) {
        cursor++;
      }
      if (cursor < source.length() && Character.isDigit(source.text().charAt(cursor))) {
        kind = TokenKind.DECIMAL;
        offset = cursor + 1;
        while (!isAtEnd()) {
          int character = source.text().codePointAt(offset);
          if (!Character.isDigit(character) && character != '_') break;
          offset += Character.charCount(character);
        }
      }
    }
    String lexeme = source.text().substring(start, offset);
    for (int index = 0; index < lexeme.length(); index++) {
      if (lexeme.charAt(index) == '_'
          && (index == 0
              || index + 1 == lexeme.length()
              || !Character.isDigit(lexeme.charAt(index - 1))
              || !Character.isDigit(lexeme.charAt(index + 1)))) {
        diagnostics.error(
            INVALID_NUMBER,
            "numeric separators must occur between digits",
            new SourceSpan(source, start, offset));
        break;
      }
    }
    tokens.add(
        new Token(kind, lexeme, lexeme.replace("_", ""), new SourceSpan(source, start, offset)));
  }

  private void scanString(int start) {
    StringBuilder value = new StringBuilder();
    while (!isAtEnd()) {
      int characterStart = offset;
      int character = advanceCodePoint();
      if (character == '"') {
        tokens.add(
            new Token(
                TokenKind.STRING,
                source.text().substring(start, offset),
                value.toString(),
                new SourceSpan(source, start, offset)));
        return;
      }
      if (character == '\n' || character == '\r') {
        diagnostics.error(
            UNTERMINATED_STRING,
            "string literal is not terminated before the end of the line",
            new SourceSpan(source, start, characterStart));
        return;
      }
      if (character != '\\') {
        if (character == '$' && startsWith("{")) {
          diagnostics.error(
              UNSUPPORTED_INTERPOLATION,
              "string interpolation is not supported",
              new SourceSpan(source, characterStart, Math.min(offset + 1, source.length())));
        }
        value.appendCodePoint(character);
        continue;
      }
      if (isAtEnd()) {
        break;
      }
      int escapeStart = offset - 1;
      int escaped = advanceCodePoint();
      switch (escaped) {
        case 'n' -> value.append('\n');
        case 'r' -> value.append('\r');
        case 't' -> value.append('\t');
        case '"' -> value.append('"');
        case '\\' -> value.append('\\');
        case '$' -> value.append('$');
        default ->
            diagnostics.error(
                INVALID_ESCAPE,
                "unsupported string escape '\\" + new String(Character.toChars(escaped)) + "'",
                new SourceSpan(source, escapeStart, offset));
      }
    }
    diagnostics.error(
        UNTERMINATED_STRING,
        "string literal is not terminated before the end of the file",
        new SourceSpan(source, start, offset));
  }

  private void scanCodePoint(int start) {
    List<Integer> values = new ArrayList<>();
    boolean valid = true;
    while (!isAtEnd()) {
      int characterStart = offset;
      int character = advanceCodePoint();
      if (character == '\'') {
        if (!valid) {
          return;
        }
        if (values.size() != 1) {
          diagnostics.error(
              INVALID_CODE_POINT,
              "code point literal must contain exactly one Unicode code point",
              new SourceSpan(source, start, offset));
          return;
        }
        tokens.add(
            new Token(
                TokenKind.CODE_POINT,
                source.text().substring(start, offset),
                Integer.toString(values.getFirst()),
                new SourceSpan(source, start, offset)));
        return;
      }
      if (character == '\n' || character == '\r') {
        diagnostics.error(
            INVALID_CODE_POINT,
            "code point literal is not terminated before the end of the line",
            new SourceSpan(source, start, characterStart));
        return;
      }
      if (character != '\\') {
        if (character >= Character.MIN_SURROGATE && character <= Character.MAX_SURROGATE) {
          diagnostics.error(
              INVALID_CODE_POINT,
              "code point literal must contain a Unicode scalar value",
              new SourceSpan(source, characterStart, offset));
          valid = false;
          continue;
        }
        values.add(character);
        continue;
      }
      if (isAtEnd()) {
        break;
      }
      int escapeStart = offset - 1;
      int escaped = advanceCodePoint();
      switch (escaped) {
        case 'n' -> values.add((int) '\n');
        case 'r' -> values.add((int) '\r');
        case 't' -> values.add((int) '\t');
        case '\'' -> values.add((int) '\'');
        case '\\' -> values.add((int) '\\');
        default -> {
          diagnostics.error(
              INVALID_ESCAPE,
              "unsupported code point escape '\\" + new String(Character.toChars(escaped)) + "'",
              new SourceSpan(source, escapeStart, offset));
          valid = false;
        }
      }
    }
    diagnostics.error(
        INVALID_CODE_POINT,
        "code point literal is not terminated before the end of the file",
        new SourceSpan(source, start, offset));
  }

  private void scanDoubleOperator(char expected, TokenKind kind, int start) {
    if (match(expected)) {
      addSimple(kind, start);
    } else {
      reportUnexpected(expected, start);
    }
  }

  private void reportUnexpected(int character, int start) {
    diagnostics.error(
        UNEXPECTED_CHARACTER,
        "unexpected character '" + new String(Character.toChars(character)) + "'",
        new SourceSpan(source, start, offset));
  }

  private boolean match(char expected) {
    if (isAtEnd() || source.text().charAt(offset) != expected) {
      return false;
    }
    offset++;
    return true;
  }

  private boolean startsWith(String value) {
    return source.text().startsWith(value, offset);
  }

  private void addSimple(TokenKind kind, int start) {
    SourceSpan span = new SourceSpan(source, start, offset);
    tokens.add(Token.simple(kind, span.text(), span));
  }

  private int advanceCodePoint() {
    int character = source.text().codePointAt(offset);
    offset += Character.charCount(character);
    return character;
  }

  private boolean isAtEnd() {
    return offset >= source.length();
  }

  private static boolean isIdentifierStart(int character) {
    return character == '_' || Character.isUnicodeIdentifierStart(character);
  }
}
