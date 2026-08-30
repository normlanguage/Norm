package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;

final class ParserRecovery {
  static boolean canInsert(TokenKind expected, Token previous, Token current) {
    return switch (expected) {
      case RIGHT_PAREN ->
          current.kind() == TokenKind.LEFT_BRACE
              || current.kind() == TokenKind.RIGHT_BRACE
              || current.kind() == TokenKind.SEMICOLON
              || current.kind() == TokenKind.END_OF_FILE
              || startsStatementOnLaterLine(previous, current);
      case RIGHT_BRACKET ->
          current.kind() == TokenKind.RIGHT_PAREN
              || current.kind() == TokenKind.RIGHT_BRACE
              || current.kind() == TokenKind.COMMA
              || current.kind() == TokenKind.SEMICOLON
              || current.kind() == TokenKind.END_OF_FILE
              || startsStatementOnLaterLine(previous, current);
      case RIGHT_BRACE ->
          current.kind() == TokenKind.ELSE
              || current.kind() == TokenKind.CATCH
              || current.kind() == TokenKind.FINALLY
              || current.kind() == TokenKind.END_OF_FILE;
      default -> false;
    };
  }

  static boolean canResumeStatement(Token token, int recoveryLine) {
    return token.span().start().line() > recoveryLine && startsStatement(token.kind());
  }

  static String display(Token token) {
    return token.kind() == TokenKind.END_OF_FILE
        ? "end of file"
        : token.lexeme().isEmpty() ? token.kind().name().toLowerCase() : "'" + token.lexeme() + "'";
  }

  private static boolean startsStatementOnLaterLine(Token previous, Token current) {
    return current.span().start().line() > previous.span().end().line()
        && startsStatement(current.kind());
  }

  private static boolean startsStatement(TokenKind kind) {
    return switch (kind) {
      case IF,
          FOR,
          TRY,
          THROW,
          RETURN,
          BREAK,
          CONTINUE,
          AT,
          VAR,
          IDENTIFIER,
          SWITCH,
          INTEGER,
          DECIMAL,
          CODE_POINT,
          STRING,
          TRUE,
          FALSE,
          NULL,
          LEFT_PAREN,
          LEFT_BRACKET,
          BANG,
          MINUS ->
          true;
      default -> false;
    };
  }

  private ParserRecovery() {}
}
